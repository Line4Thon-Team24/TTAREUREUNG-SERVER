package com.ttareureung.api.domain.senior.service;

import com.ttareureung.api.domain.notification.entity.NotificationSetting;
import com.ttareureung.api.domain.notification.repository.NotificationSettingRepository;
import com.ttareureung.api.domain.profile.entity.GuardianSeniorStatus;
import com.ttareureung.api.domain.profile.service.ProfileAccessChecker;
import com.ttareureung.api.domain.push.service.PushNotifier;
import com.ttareureung.api.domain.senior.dto.GuardianAlertListResponse;
import com.ttareureung.api.domain.senior.dto.GuardianAlertResponse;
import com.ttareureung.api.domain.senior.dto.UpcomingCall;
import com.ttareureung.api.domain.senior.entity.DoseSession;
import com.ttareureung.api.domain.senior.entity.GuardianAlert;
import com.ttareureung.api.domain.senior.entity.GuardianAlertType;
import com.ttareureung.api.domain.senior.exception.SeniorErrorCode;
import com.ttareureung.api.domain.senior.repository.DoseSessionRepository;
import com.ttareureung.api.domain.senior.repository.GuardianAlertRepository;
import com.ttareureung.api.global.exception.CustomException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 보호자 알림 생성·조회·읽음.
 * 알림은 항상 저장하고, 보호자별 알림 설정(ON/OFF)은 조회 시 profileId로 걸러냄
 */
@Service
@Transactional
public class GuardianAlertService {

    private final ProfileAccessChecker accessChecker;
    private final GuardianAlertRepository alerts;
    private final DoseSessionRepository sessions;
    private final NotificationSettingRepository settings;
    private final PushNotifier pushNotifier;
    private final Clock clock;

    public GuardianAlertService(ProfileAccessChecker accessChecker, GuardianAlertRepository alerts,
                                DoseSessionRepository sessions, NotificationSettingRepository settings,
                                PushNotifier pushNotifier, Clock clock) {
        this.accessChecker = accessChecker;
        this.alerts = alerts;
        this.sessions = sessions;
        this.settings = settings;
        this.pushNotifier = pushNotifier;
        this.clock = clock;
    }

    // 미복용은 재전화마다 반복 생성하지 않고 회차당 1건
    public GuardianAlert notify(DoseSession session, GuardianAlertType type) {
        if (type == GuardianAlertType.MISSED && alerts.existsBySessionIdAndType(session.getId(), type)) {
            return null;
        }
        GuardianAlert saved = alerts.save(new GuardianAlert(session, type, now()));
        pushNotifier.guardianAlert(saved);
        return saved;
    }

    public boolean exists(DoseSession session, GuardianAlertType type) {
        return alerts.existsBySessionIdAndType(session.getId(), type);
    }

    @Transactional(readOnly = true)
    public GuardianAlertListResponse list(Long accountId, Long profileId, boolean unreadOnly) {
        NotificationSetting setting = profileId == null ? null : settingOf(accountId, profileId);
        List<GuardianAlertResponse> items = alerts.findAllAccessible(accountId, GuardianSeniorStatus.ACTIVE).stream()
                .filter(a -> !unreadOnly || a.getReadAt() == null)
                .filter(a -> setting == null || wanted(setting, a.getType()))
                .map(GuardianAlertResponse::from)
                .toList();
        int remindMinutes = setting == null ? 10 : setting.getRemindBeforeMinutes();
        return new GuardianAlertListResponse(items, upcoming(accountId, remindMinutes));
    }

    public GuardianAlertResponse markRead(Long accountId, Long alertId) {
        accessChecker.activeAccount(accountId);
        GuardianAlert alert = alerts.findAccessibleById(alertId, accountId, GuardianSeniorStatus.ACTIVE)
                .orElseThrow(() -> new CustomException(SeniorErrorCode.ALERT_NOT_FOUND));
        alert.markRead(now());
        return GuardianAlertResponse.from(alert);
    }

    private NotificationSetting settingOf(Long accountId, Long profileId) {
        accessChecker.ownedProfile(accountId, profileId);
        return settings.findByProfileId(profileId).orElseGet(() -> NotificationSetting.defaultsFor(profileId));
    }

    // 프로필 알림 설정과 알림 종류 대응. 도움 요청은 설정과 무관하게 항상 표시
    private static boolean wanted(NotificationSetting s, GuardianAlertType type) {
        return switch (type) {
            case COMPLETED -> s.isNotifyOnComplete();
            case MISSED, PARTIAL -> s.isNotifyOnMiss();
            case HELP_REQUEST -> true;
        };
    }

    // "N분 후 복용 예정". remindMinutes가 0이면 끔
    private List<UpcomingCall> upcoming(Long accountId, int remindMinutes) {
        if (remindMinutes <= 0) {
            return List.of();
        }
        LocalDateTime now = now();
        return sessions.findUpcomingAccessible(accountId, GuardianSeniorStatus.ACTIVE, now, now.plusMinutes(remindMinutes))
                .stream()
                .map(s -> new UpcomingCall(s.getSenior().getId(), s.getSenior().getName(), s.getId(),
                        s.getTimeSlot(), s.getTimeSlot().getLabel(), s.getNextCallAt(),
                        (int) Duration.between(now, s.getNextCallAt()).toMinutes()))
                .toList();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(SeniorTime.ZONE));
    }
}
