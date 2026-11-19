package com.ttareureung.api.domain.push.service;

import com.ttareureung.api.domain.medication.entity.MealTimeSlot;
import com.ttareureung.api.domain.medication.entity.MedicationLog;
import com.ttareureung.api.domain.medication.repository.MedicationLogRepository;
import com.ttareureung.api.domain.notification.entity.NotificationSetting;
import com.ttareureung.api.domain.profile.entity.Profile;
import com.ttareureung.api.domain.senior.entity.DoseSession;
import com.ttareureung.api.domain.senior.repository.DoseSessionRepository;
import com.ttareureung.api.domain.senior.service.DoseScheduleService;
import com.ttareureung.api.domain.senior.service.DoseTimeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 1분마다 실행: 오늘 회차 생성 → 전화 시각이 된 회차에 시니어 전화 푸시 → 보호자 복용 임박 푸시.
 * 앱이 꺼져 있어도 알림이 가도록 서버가 직접 시각을 확인
 */
@Slf4j
@Component
public class DosePushJob {

    // 서버가 오래 꺼져 있다 켜졌을 때 지난 회차를 한꺼번에 울리지 않도록 이 시간 안의 회차만 전화
    private static final int CALL_GRACE_MINUTES = 30;

    private final DoseSessionRepository sessions;
    private final MedicationLogRepository logs;
    private final DoseScheduleService scheduleService;
    private final DoseTimeService doseTimeService;
    private final PushNotifier notifier;
    private final TransactionTemplate tx;

    public DosePushJob(DoseSessionRepository sessions, MedicationLogRepository logs, DoseScheduleService scheduleService,
                       DoseTimeService doseTimeService, PushNotifier notifier, TransactionTemplate tx) {
        this.sessions = sessions;
        this.logs = logs;
        this.scheduleService = scheduleService;
        this.doseTimeService = doseTimeService;
        this.notifier = notifier;
        this.tx = tx;
    }

    // previousRun~now 사이에 "N분 전" 시각이 지난 회차만 임박 푸시. 실행 간격이 흔들려도 중복·누락 최소화
    public void runOnce(LocalDateTime previousRun, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        for (Long seniorId : sessions.findSeniorIdsWithLogsOn(today)) {
            try {
                Map<MealTimeSlot, List<MedicationLog>> bySlot = logs.findDailyLogs(seniorId, today).stream()
                        .collect(Collectors.groupingBy(MedicationLog::getMealTimeSlot, () -> new EnumMap<>(MealTimeSlot.class),
                                Collectors.toList()));
                scheduleService.ensureSessions(seniorId, today, bySlot.keySet(), doseTimeService.effectiveTimes(seniorId));
            } catch (RuntimeException e) {
                log.warn("회차 생성 실패 senior={}: {}", seniorId, e.toString());
            }
        }
        tx.executeWithoutResult(status -> {
            for (DoseSession session : sessions.findScheduledOn(today)) {
                pushCallIfDue(session, now);
                pushUpcoming(session, previousRun, now);
            }
        });
    }

    private void pushCallIfDue(DoseSession session, LocalDateTime now) {
        if (!session.needsCallPush(now) || session.getNextCallAt().isBefore(now.minusMinutes(CALL_GRACE_MINUTES))) {
            return;
        }
        int medicationCount = (int) logs.findDailyLogs(session.getSenior().getId(), session.getTargetDate()).stream()
                .filter(l -> l.getMealTimeSlot() == session.getTimeSlot())
                .count();
        session.markCallPushed(now);
        notifier.doseCall(session, medicationCount);
    }

    // 첫 전화 전 회차만. 재전화 대기 중인 회차는 이미 보호자가 미복용 알림을 받음
    private void pushUpcoming(DoseSession session, LocalDateTime previousRun, LocalDateTime now) {
        if (session.getCallAttempts() > 0 || session.getNextCallAt() == null || !session.getNextCallAt().isAfter(now)) {
            return;
        }
        for (Profile guardian : notifier.guardiansOf(session.getSenior().getId())) {
            NotificationSetting setting = notifier.settingOf(guardian.getId());
            int minutes = setting.getRemindBeforeMinutes();
            if (minutes <= 0) {
                continue;
            }
            LocalDateTime remindAt = session.getNextCallAt().minusMinutes(minutes);
            if (remindAt.isAfter(previousRun) && !remindAt.isAfter(now)) {
                notifier.upcoming(session, guardian, minutes);
            }
        }
    }
}
