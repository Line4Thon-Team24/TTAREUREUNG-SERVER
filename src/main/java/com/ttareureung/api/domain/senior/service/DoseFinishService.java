package com.ttareureung.api.domain.senior.service;

import com.ttareureung.api.domain.medication.entity.MedicationLog;
import com.ttareureung.api.domain.medication.entity.MedicationStatus;
import com.ttareureung.api.domain.profile.service.ProfileAccessChecker;
import com.ttareureung.api.domain.senior.dto.FinishResponse;
import com.ttareureung.api.domain.senior.dto.HelpResponse;
import com.ttareureung.api.domain.senior.dto.NextCallInfo;
import com.ttareureung.api.domain.senior.entity.DoseSession;
import com.ttareureung.api.domain.senior.entity.DoseSessionStatus;
import com.ttareureung.api.domain.senior.entity.GuardianAlert;
import com.ttareureung.api.domain.senior.entity.GuardianAlertType;
import com.ttareureung.api.domain.senior.exception.SeniorErrorCode;
import com.ttareureung.api.global.exception.CustomException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 결과 화면(회차 종료)과 도움 요청.
 * 전부 복용 → COMPLETED, 일부 → PARTIAL(못 먹은 약 하루 연장), 하나도 안 먹음 → 10분 뒤 재전화(3회째 MISSED)
 */
@Service
@Transactional
public class DoseFinishService {

    private final ProfileAccessChecker accessChecker;
    private final DoseSessionFinder finder;
    private final DoseCallService callService;
    private final DoseSessionOutcome outcome;
    private final MedicationExtensionService extension;
    private final GuardianAlertService alertService;
    private final Clock clock;

    public DoseFinishService(ProfileAccessChecker accessChecker, DoseSessionFinder finder, DoseCallService callService,
                             DoseSessionOutcome outcome, MedicationExtensionService extension,
                             GuardianAlertService alertService, Clock clock) {
        this.accessChecker = accessChecker;
        this.finder = finder;
        this.callService = callService;
        this.outcome = outcome;
        this.extension = extension;
        this.alertService = alertService;
        this.clock = clock;
    }

    public FinishResponse finish(Long accountId, Long sessionId) {
        accessChecker.activeAccount(accountId);
        DoseSession session = finder.lockedAccessible(accountId, sessionId);
        finder.requireInProgress(session);
        LocalDateTime now = now();
        List<MedicationLog> slotLogs = finder.logsOf(session);
        int total = slotLogs.size();
        int taken = (int) slotLogs.stream().filter(l -> l.getStatus() == MedicationStatus.TAKEN).count();

        int extended = 0;
        boolean notified;
        String voiceText;
        NextCallInfo nextCall;
        if (taken == total) {
            session.finish(DoseSessionStatus.COMPLETED, now);
            notified = alertService.notify(session, GuardianAlertType.COMPLETED) != null;
            nextCall = callService.nextCallAfter(session);
            voiceText = VoiceText.completed(nextCall == null ? null : nextCall.scheduledAt());
        } else if (taken == 0) {
            // 전화 미응답과 같은 규칙: 횟수 누적, 10분 뒤 재전화, 3회째 미복용 확정
            session.recordNoAnswer(now, DoseCallService.RETRY_MINUTES, DoseCallService.MAX_CALL_ATTEMPTS);
            if (session.getStatus().isFinished()) {
                extended = outcome.missRemaining(session, slotLogs);
                notified = true;
                nextCall = callService.nextCallAfter(session);
                voiceText = VoiceText.missed();
            } else {
                notified = alertService.notify(session, GuardianAlertType.MISSED) != null
                        || alertExists(session, GuardianAlertType.MISSED);
                nextCall = new NextCallInfo(session.getNextCallAt(), session.getTimeSlot(),
                        session.getTimeSlot().getLabel(), names(slotLogs));
                voiceText = VoiceText.retryLater(DoseCallService.RETRY_MINUTES);
            }
        } else {
            List<MedicationLog> pending = slotLogs.stream()
                    .filter(l -> l.getStatus() == MedicationStatus.PENDING)
                    .toList();
            pending.forEach(l -> l.changeStatus(MedicationStatus.MISSED));
            extended = extension.extendOneDay(pending);
            session.finish(DoseSessionStatus.PARTIAL, now);
            notified = alertService.notify(session, GuardianAlertType.PARTIAL) != null;
            nextCall = callService.nextCallAfter(session);
            voiceText = VoiceText.partial(pending.size());
        }
        return new FinishResponse(session.getId(), session.getStatus(), taken, total, extended, notified, nextCall, voiceText);
    }

    public HelpResponse help(Long accountId, Long sessionId) {
        accessChecker.activeAccount(accountId);
        DoseSession session = finder.accessible(accountId, sessionId);
        if (session.getStatus().isFinished()) {
            throw new CustomException(SeniorErrorCode.SESSION_FINISHED);
        }
        GuardianAlert alert = alertService.notify(session, GuardianAlertType.HELP_REQUEST);
        return new HelpResponse(alert.getId(), session.getId(), alert.getCreatedAt(), VoiceText.helpRequested());
    }

    private boolean alertExists(DoseSession session, GuardianAlertType type) {
        return alertService.exists(session, type);
    }

    private static List<String> names(List<MedicationLog> logs) {
        return logs.stream().map(l -> l.getMedication().getDisplayName()).toList();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(SeniorTime.ZONE));
    }
}
