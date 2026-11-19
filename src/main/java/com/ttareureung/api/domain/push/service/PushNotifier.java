package com.ttareureung.api.domain.push.service;

import com.ttareureung.api.domain.notification.entity.NotificationSetting;
import com.ttareureung.api.domain.notification.repository.NotificationSettingRepository;
import com.ttareureung.api.domain.profile.entity.GuardianSenior;
import com.ttareureung.api.domain.profile.entity.GuardianSeniorStatus;
import com.ttareureung.api.domain.profile.entity.Profile;
import com.ttareureung.api.domain.profile.repository.GuardianSeniorRepository;
import com.ttareureung.api.domain.push.client.PushGateway;
import com.ttareureung.api.domain.push.entity.PushSubscription;
import com.ttareureung.api.domain.push.repository.PushSubscriptionRepository;
import com.ttareureung.api.domain.senior.dto.GuardianAlertResponse;
import com.ttareureung.api.domain.senior.entity.DoseSession;
import com.ttareureung.api.domain.senior.entity.GuardianAlert;
import com.ttareureung.api.domain.senior.entity.GuardianAlertType;
import com.ttareureung.api.domain.senior.service.VoiceText;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 푸시 메시지 조립·발송.
 * 메시지는 호출한 트랜잭션 안에서 만들고(지연 로딩), 발송은 커밋 후 별도 스레드에서 진행해 API 응답을 막지 않음
 */
@Slf4j
@Component
public class PushNotifier {

    private final PushGateway gateway;
    private final PushSubscriptionRepository subscriptions;
    private final GuardianSeniorRepository links;
    private final NotificationSettingRepository settings;
    private final TransactionTemplate tx;
    private final JsonMapper jsonMapper;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public PushNotifier(PushGateway gateway, PushSubscriptionRepository subscriptions, GuardianSeniorRepository links,
                        NotificationSettingRepository settings, TransactionTemplate tx, JsonMapper jsonMapper) {
        this.gateway = gateway;
        this.subscriptions = subscriptions;
        this.links = links;
        this.settings = settings;
        this.tx = tx;
        this.jsonMapper = jsonMapper;
    }

    // 보호자 알림: 시니어와 연결된 보호자 중 해당 알림을 켠 보호자에게. 도움 요청은 설정과 무관하게 발송
    public void guardianAlert(GuardianAlert alert) {
        if (!gateway.enabled()) {
            return;
        }
        GuardianAlertResponse view = GuardianAlertResponse.from(alert);
        List<Long> guardianIds = guardiansOf(alert.getSenior().getId()).stream()
                .filter(g -> wanted(settingOf(g.getId()), alert.getType()))
                .map(Profile::getId)
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "GUARDIAN_ALERT");
        payload.put("alertId", alert.getId());
        payload.put("alertType", alert.getType().name());
        payload.put("seniorProfileId", alert.getSenior().getId());
        payload.put("sessionId", alert.getSession().getId());
        payload.put("title", title(alert.getType()));
        payload.put("body", view.message());
        dispatch(guardianIds, payload);
    }

    // 시니어 전화: 회차 시각(재전화 포함)이 됐을 때 시니어 프로필 기기로
    public void doseCall(DoseSession session, int medicationCount) {
        if (!gateway.enabled()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "DOSE_CALL");
        payload.put("sessionId", session.getId());
        payload.put("seniorProfileId", session.getSenior().getId());
        payload.put("title", "띠링이");
        payload.put("body", VoiceText.callGreeting(session.getTimeSlot(), medicationCount));
        dispatch(List.of(session.getSenior().getId()), payload);
    }

    // 복용 임박: 보호자별 "N분 전" 설정에 맞춰 한 명씩 발송
    public void upcoming(DoseSession session, Profile guardian, int minutesLeft) {
        if (!gateway.enabled()) {
            return;
        }
        Profile senior = session.getSenior();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "DOSE_UPCOMING");
        payload.put("sessionId", session.getId());
        payload.put("seniorProfileId", senior.getId());
        payload.put("title", "약 복용 예정");
        payload.put("body", senior.getName() + "님께서 " + minutesLeft + "분 후 "
                + VoiceText.koreanTime(session.getNextCallAt()) + " 약 복용 예정이에요.");
        dispatch(List.of(guardian.getId()), payload);
    }

    // 연결 상태가 ACTIVE인 보호자만
    public List<Profile> guardiansOf(Long seniorId) {
        return links.findAllByProfileId(seniorId).stream()
                .filter(l -> l.getSenior().getId().equals(seniorId))
                .filter(l -> l.getStatus() == GuardianSeniorStatus.ACTIVE)
                .map(GuardianSenior::getGuardian)
                .toList();
    }

    public NotificationSetting settingOf(Long profileId) {
        return settings.findByProfileId(profileId).orElseGet(() -> NotificationSetting.defaultsFor(profileId));
    }

    private static boolean wanted(NotificationSetting s, GuardianAlertType type) {
        return switch (type) {
            case COMPLETED -> s.isNotifyOnComplete();
            case MISSED, PARTIAL -> s.isNotifyOnMiss();
            case HELP_REQUEST -> true;
        };
    }

    private static String title(GuardianAlertType type) {
        return switch (type) {
            case COMPLETED -> "약 복용 완료";
            case PARTIAL -> "약 일부 복용";
            case MISSED -> "약 미복용";
            case HELP_REQUEST -> "도움 요청";
        };
    }

    private void dispatch(List<Long> profileIds, Map<String, Object> payload) {
        if (profileIds.isEmpty()) {
            return;
        }
        List<Target> targets = subscriptions.findAllByProfileIdIn(profileIds).stream()
                .map(s -> new Target(s.getEndpoint(), s.getP256dh(), s.getAuth()))
                .toList();
        if (targets.isEmpty()) {
            return;
        }
        byte[] body = jsonMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
        Runnable send = () -> targets.forEach(t -> executor.submit(() -> sendOne(t, body)));
        // 롤백된 알림은 보내지 않도록 커밋 이후 발송
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    private void sendOne(Target target, byte[] body) {
        int status = gateway.send(target.endpoint(), target.p256dh(), target.auth(), body);
        // 404·410: 브라우저가 구독을 취소했거나 만료. 다음부터 보내지 않도록 삭제
        if (status == 404 || status == 410) {
            tx.executeWithoutResult(s -> subscriptions.deleteByEndpoint(target.endpoint()));
            log.info("만료된 푸시 구독 삭제");
        }
    }

    private record Target(String endpoint, String p256dh, String auth) {
    }
}
