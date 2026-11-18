package com.ttareureung.api.domain.senior.service;

import com.ttareureung.api.domain.file.service.FileUrlResolver;
import com.ttareureung.api.domain.guardian.service.SeniorAccessValidator;
import com.ttareureung.api.domain.medication.entity.MealTimeSlot;
import com.ttareureung.api.domain.medication.entity.MedicationLog;
import com.ttareureung.api.domain.medication.entity.MedicationStatus;
import com.ttareureung.api.domain.medication.repository.MedicationLogRepository;
import com.ttareureung.api.domain.senior.dto.ScheduleResponse;
import com.ttareureung.api.domain.senior.entity.DoseSession;
import com.ttareureung.api.domain.senior.entity.DoseSessionStatus;
import com.ttareureung.api.domain.senior.repository.DoseSessionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 날짜별 복용 일정 조회.
 * 회차 행은 오늘 날짜 조회 때만 생성. 과거·미래는 복용 기록으로 상태 계산
 */
@Service
public class DoseScheduleService {

    private final SeniorAccessValidator seniorAccess;
    private final MedicationLogRepository logs;
    private final DoseSessionRepository sessions;
    private final DoseSessionCreator creator;
    private final DoseTimeService doseTimeService;
    private final FileUrlResolver urls;
    private final Clock clock;

    public DoseScheduleService(SeniorAccessValidator seniorAccess, MedicationLogRepository logs,
                               DoseSessionRepository sessions, DoseSessionCreator creator,
                               DoseTimeService doseTimeService, FileUrlResolver urls, Clock clock) {
        this.seniorAccess = seniorAccess;
        this.logs = logs;
        this.sessions = sessions;
        this.creator = creator;
        this.doseTimeService = doseTimeService;
        this.urls = urls;
        this.clock = clock;
    }

    // 트랜잭션 없음: REPEATABLE READ 스냅샷 안에서는 별도 트랜잭션이 만든 회차 행이 보이지 않음
    public ScheduleResponse getSchedule(Long accountId, Long seniorId, LocalDate date) {
        seniorAccess.requireSenior(accountId, seniorId);
        LocalDate today = LocalDate.now(clock.withZone(SeniorTime.ZONE));
        LocalDate target = date == null ? today : date;
        boolean isToday = target.equals(today);

        Map<MealTimeSlot, List<MedicationLog>> logsBySlot = new EnumMap<>(MealTimeSlot.class);
        for (MedicationLog log : logs.findDailyLogs(seniorId, target)) {
            logsBySlot.computeIfAbsent(log.getMealTimeSlot(), k -> new ArrayList<>()).add(log);
        }
        Map<MealTimeSlot, LocalTime> times = doseTimeService.effectiveTimes(seniorId);

        Map<MealTimeSlot, DoseSession> sessionBySlot = new EnumMap<>(MealTimeSlot.class);
        if (isToday) {
            sessionBySlot = ensureSessions(seniorId, today, logsBySlot.keySet(), times);
        }

        int taken = 0;
        int total = 0;
        List<ScheduleResponse.Session> items = new ArrayList<>();
        for (MealTimeSlot slot : MealTimeSlot.values()) {
            List<MedicationLog> slotLogs = logsBySlot.get(slot);
            if (slotLogs == null) {
                continue;
            }
            DoseSession session = sessionBySlot.get(slot);
            List<ScheduleResponse.Medication> medications = slotLogs.stream()
                    .map(l -> new ScheduleResponse.Medication(l.getMedication().getId(),
                            l.getMedication().getDisplayName(), l.getMedication().getDoseAmount(),
                            l.getMedication().getStrength(), urls.resolve(l.getMedication().getImageUrl()), l.getStatus()))
                    .toList();
            long takenInSlot = slotLogs.stream().filter(l -> l.getStatus() == MedicationStatus.TAKEN).count();
            taken += (int) takenInSlot;
            total += slotLogs.size();

            items.add(new ScheduleResponse.Session(
                    session == null ? null : session.getId(),
                    slot,
                    slot.getLabel(),
                    session == null ? times.get(slot) : session.getScheduledAt().toLocalTime(),
                    status(session, takenInSlot, slotLogs.size(), target, today),
                    session == null ? null : session.getAnsweredAt(),
                    medications));
        }
        return new ScheduleResponse(target, isToday, taken, total, items);
    }

    // 약이 있는 회차만 생성. 동시 요청으로 이미 만들어진 경우는 다시 조회로 흡수. 전화 흐름(current)에서도 사용
    public Map<MealTimeSlot, DoseSession> ensureSessions(Long seniorId, LocalDate today,
                                                         Iterable<MealTimeSlot> slots,
                                                         Map<MealTimeSlot, LocalTime> times) {
        Map<MealTimeSlot, DoseSession> existing = sessionsOf(seniorId, today);
        boolean created = false;
        for (MealTimeSlot slot : slots) {
            if (existing.containsKey(slot)) {
                continue;
            }
            try {
                creator.create(seniorId, today, slot, today.atTime(times.get(slot)));
                created = true;
            } catch (DataIntegrityViolationException ignored) {
                created = true;
            }
        }
        return created ? sessionsOf(seniorId, today) : existing;
    }

    private Map<MealTimeSlot, DoseSession> sessionsOf(Long seniorId, LocalDate date) {
        Map<MealTimeSlot, DoseSession> result = new EnumMap<>(MealTimeSlot.class);
        sessions.findAllBySeniorIdAndTargetDate(seniorId, date).forEach(s -> result.put(s.getTimeSlot(), s));
        return result;
    }

    // 회차가 끝났으면 그 결과, 아니면 복용 기록으로 판단. 보호자가 기록을 직접 바꾼 경우도 반영
    private static DoseSessionStatus status(DoseSession session, long taken, int total,
                                            LocalDate target, LocalDate today) {
        if (session != null && session.getStatus().isFinished()) {
            return session.getStatus();
        }
        if (taken == total) {
            return DoseSessionStatus.COMPLETED;
        }
        if (session != null) {
            return session.getStatus();
        }
        if (target.isBefore(today)) {
            return taken > 0 ? DoseSessionStatus.PARTIAL : DoseSessionStatus.MISSED;
        }
        return DoseSessionStatus.SCHEDULED;
    }
}
