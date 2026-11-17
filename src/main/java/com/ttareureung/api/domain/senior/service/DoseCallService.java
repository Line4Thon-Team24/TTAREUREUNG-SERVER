package com.ttareureung.api.domain.senior.service;

import com.ttareureung.api.domain.file.service.FileUrlResolver;
import com.ttareureung.api.domain.guardian.service.SeniorAccessValidator;
import com.ttareureung.api.domain.medication.entity.MealTimeSlot;
import com.ttareureung.api.domain.medication.entity.MedicationLog;
import com.ttareureung.api.domain.medication.entity.MedicationStatus;
import com.ttareureung.api.domain.medication.repository.MedicationLogRepository;
import com.ttareureung.api.domain.profile.service.ProfileAccessChecker;
import com.ttareureung.api.domain.senior.dto.CurrentSessionResponse;
import com.ttareureung.api.domain.senior.dto.MedicationGuide;
import com.ttareureung.api.domain.senior.dto.NextCallInfo;
import com.ttareureung.api.domain.senior.dto.NoAnswerResponse;
import com.ttareureung.api.domain.senior.dto.SessionCallResponse;
import com.ttareureung.api.domain.senior.entity.DoseSession;
import com.ttareureung.api.domain.senior.exception.SeniorErrorCode;
import com.ttareureung.api.domain.senior.repository.DoseSessionRepository;
import com.ttareureung.api.global.exception.CustomException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 복약 전화 흐름: 지금 울려야 할 회차 확인 → 전화 받기 → 미응답 재전화.
 * 푸시 없이 프론트가 1분 간격으로 current를 조회하는 구조
 */
@Service
public class DoseCallService {

    // 결과 화면 문구 "10분 뒤 다시 전화", 3회 실패 시 미복용 확정
    static final int RETRY_MINUTES = 10;
    static final int MAX_CALL_ATTEMPTS = 3;

    private final ProfileAccessChecker accessChecker;
    private final SeniorAccessValidator seniorAccess;
    private final MedicationLogRepository logs;
    private final DoseSessionRepository sessions;
    private final DoseSessionFinder finder;
    private final DoseScheduleService scheduleService;
    private final DoseTimeService doseTimeService;
    private final DoseSessionOutcome outcome;
    private final FileUrlResolver urls;
    private final Clock clock;

    public DoseCallService(ProfileAccessChecker accessChecker, SeniorAccessValidator seniorAccess,
                           MedicationLogRepository logs, DoseSessionRepository sessions, DoseSessionFinder finder,
                           DoseScheduleService scheduleService, DoseTimeService doseTimeService,
                           DoseSessionOutcome outcome, FileUrlResolver urls, Clock clock) {
        this.accessChecker = accessChecker;
        this.seniorAccess = seniorAccess;
        this.logs = logs;
        this.sessions = sessions;
        this.finder = finder;
        this.scheduleService = scheduleService;
        this.doseTimeService = doseTimeService;
        this.outcome = outcome;
        this.urls = urls;
        this.clock = clock;
    }

    // 트랜잭션 없음: 회차 생성이 별도 트랜잭션이라 같은 스냅샷 안에서는 보이지 않음
    public CurrentSessionResponse current(Long accountId, Long seniorId) {
        seniorAccess.requireSenior(accountId, seniorId);
        LocalDateTime now = now();
        LocalDate today = now.toLocalDate();

        Map<MealTimeSlot, List<MedicationLog>> logsBySlot = logsBySlot(seniorId, today);
        Map<MealTimeSlot, LocalTime> times = doseTimeService.effectiveTimes(seniorId);
        Map<MealTimeSlot, DoseSession> todays = scheduleService.ensureSessions(seniorId, today, logsBySlot.keySet(), times);

        DoseSession current = null;
        for (MealTimeSlot slot : MealTimeSlot.values()) {
            DoseSession session = todays.get(slot);
            if (session != null && session.isDue(now)) {
                current = session;
                break;
            }
        }
        SessionCallResponse currentResponse = current == null
                ? null
                : toCallResponse(current, logsBySlot.get(current.getTimeSlot()));
        NextCallInfo next = nextCall(seniorId, today, todays, logsBySlot, times, current);
        return new CurrentSessionResponse(now, currentResponse, next);
    }

    @Transactional
    public SessionCallResponse answer(Long accountId, Long sessionId) {
        accessChecker.activeAccount(accountId);
        DoseSession session = finder.lockedAccessible(accountId, sessionId);
        if (session.getStatus().isFinished()) {
            throw new CustomException(SeniorErrorCode.SESSION_FINISHED);
        }
        session.answer(now());
        return toCallResponse(session, finder.logsOf(session));
    }

    @Transactional
    public NoAnswerResponse noAnswer(Long accountId, Long sessionId) {
        accessChecker.activeAccount(accountId);
        DoseSession session = finder.lockedAccessible(accountId, sessionId);
        if (session.getStatus().isFinished()) {
            throw new CustomException(SeniorErrorCode.SESSION_FINISHED);
        }
        if (!session.isWaitingForCall()) {
            throw new CustomException(SeniorErrorCode.SESSION_NOT_WAITING);
        }
        session.recordNoAnswer(now(), RETRY_MINUTES, MAX_CALL_ATTEMPTS);
        if (session.getStatus().isFinished()) {
            // 끝내 못 받은 회차: 약 전부 미복용, 하루 연장, 보호자 알림
            outcome.missRemaining(session, finder.logsOf(session));
        }
        return new NoAnswerResponse(session.getId(), session.getStatus(), session.getCallAttempts(), session.getNextCallAt());
    }

    // 결과 화면 "다음 알림 전화" 계산용. 회차 종료 트랜잭션 안에서 호출
    public NextCallInfo nextCallAfter(DoseSession current) {
        Long seniorId = current.getSenior().getId();
        LocalDate today = current.getTargetDate();
        Map<MealTimeSlot, DoseSession> todays = new EnumMap<>(MealTimeSlot.class);
        sessions.findAllBySeniorIdAndTargetDate(seniorId, today).forEach(s -> todays.put(s.getTimeSlot(), s));
        return nextCall(seniorId, today, todays, logsBySlot(seniorId, today), doseTimeService.effectiveTimes(seniorId), current);
    }

    private SessionCallResponse toCallResponse(DoseSession session, List<MedicationLog> slotLogs) {
        List<MedicationLog> safeLogs = slotLogs == null ? List.of() : slotLogs;
        List<MedicationGuide> guides = new ArrayList<>();
        int order = 1;
        for (MedicationLog log : safeLogs) {
            if (log.getStatus() == MedicationStatus.TAKEN) {
                continue;
            }
            guides.add(MedicationGuide.of(order, log.getMedication(), urls.resolve(log.getMedication().getImageUrl())));
            order++;
        }
        return new SessionCallResponse(session.getId(), session.getTimeSlot(), session.getTimeSlot().getLabel(),
                session.getScheduledAt(), session.getStatus(), session.getCallAttempts(), safeLogs.size(),
                VoiceText.callGreeting(session.getTimeSlot(), guides.size()), guides);
    }

    // 오늘 남은 회차 중 current 다음 것, 없으면 내일 첫 회차
    private NextCallInfo nextCall(Long seniorId, LocalDate today, Map<MealTimeSlot, DoseSession> todays,
                                  Map<MealTimeSlot, List<MedicationLog>> todayLogs,
                                  Map<MealTimeSlot, LocalTime> times, DoseSession current) {
        boolean afterCurrent = current == null;
        for (MealTimeSlot slot : MealTimeSlot.values()) {
            DoseSession session = todays.get(slot);
            if (session == null) {
                continue;
            }
            if (!afterCurrent) {
                afterCurrent = session.getId().equals(current.getId());
                continue;
            }
            if (!session.getStatus().isFinished()) {
                return new NextCallInfo(session.getScheduledAt(), slot, slot.getLabel(), names(todayLogs.get(slot)));
            }
        }
        LocalDate tomorrow = today.plusDays(1);
        Map<MealTimeSlot, List<MedicationLog>> tomorrowLogs = logsBySlot(seniorId, tomorrow);
        for (MealTimeSlot slot : MealTimeSlot.values()) {
            if (tomorrowLogs.containsKey(slot)) {
                return new NextCallInfo(tomorrow.atTime(times.get(slot)), slot, slot.getLabel(), names(tomorrowLogs.get(slot)));
            }
        }
        return null;
    }

    private Map<MealTimeSlot, List<MedicationLog>> logsBySlot(Long seniorId, LocalDate date) {
        Map<MealTimeSlot, List<MedicationLog>> result = new EnumMap<>(MealTimeSlot.class);
        for (MedicationLog log : logs.findDailyLogs(seniorId, date)) {
            result.computeIfAbsent(log.getMealTimeSlot(), k -> new ArrayList<>()).add(log);
        }
        return result;
    }

    private static List<String> names(List<MedicationLog> slotLogs) {
        return slotLogs.stream().map(l -> l.getMedication().getDisplayName()).toList();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(SeniorTime.ZONE));
    }
}
