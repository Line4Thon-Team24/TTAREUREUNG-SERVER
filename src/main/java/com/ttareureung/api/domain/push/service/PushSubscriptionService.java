package com.ttareureung.api.domain.push.service;

import com.ttareureung.api.domain.profile.entity.Profile;
import com.ttareureung.api.domain.profile.service.ProfileAccessChecker;
import com.ttareureung.api.domain.push.client.PushGateway;
import com.ttareureung.api.domain.push.client.PushProperties;
import com.ttareureung.api.domain.push.client.WebPushCrypto;
import com.ttareureung.api.domain.push.dto.PushDtos;
import com.ttareureung.api.domain.push.entity.PushSubscription;
import com.ttareureung.api.domain.push.exception.PushErrorCode;
import com.ttareureung.api.domain.push.repository.PushSubscriptionRepository;
import com.ttareureung.api.global.exception.CustomException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 푸시 구독 등록·해제. 프로필은 내 계정 소속만 허용
 */
@Service
@Transactional
public class PushSubscriptionService {

    private final ProfileAccessChecker accessChecker;
    private final PushSubscriptionRepository subscriptions;
    private final PushGateway gateway;
    private final PushProperties properties;

    public PushSubscriptionService(ProfileAccessChecker accessChecker, PushSubscriptionRepository subscriptions,
                                   PushGateway gateway, PushProperties properties) {
        this.accessChecker = accessChecker;
        this.subscriptions = subscriptions;
        this.gateway = gateway;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public PushDtos.VapidKeyResponse vapidKey() {
        return gateway.enabled()
                ? new PushDtos.VapidKeyResponse(true, properties.vapidPublicKey().trim())
                : new PushDtos.VapidKeyResponse(false, null);
    }

    // 같은 기기 재등록은 갱신. 공용 기기에서 프로필을 바꾼 경우 새 프로필로 이동
    public PushDtos.SubscribeResponse subscribe(Long accountId, PushDtos.SubscribeRequest request) {
        accessChecker.activeAccount(accountId);
        Profile profile = accessChecker.ownedProfile(accountId, request.profileId());
        validateKeys(request.keys());
        PushSubscription subscription = subscriptions.findByEndpoint(request.endpoint())
                .map(existing -> {
                    existing.renew(profile, request.keys().p256dh(), request.keys().auth());
                    return existing;
                })
                .orElseGet(() -> subscriptions.save(new PushSubscription(profile, request.endpoint(),
                        request.keys().p256dh(), request.keys().auth())));
        return new PushDtos.SubscribeResponse(subscription.getId(), profile.getId());
    }

    // 다른 계정이 등록한 endpoint는 없는 것처럼 404
    public void unsubscribe(Long accountId, PushDtos.UnsubscribeRequest request) {
        accessChecker.activeAccount(accountId);
        PushSubscription subscription = subscriptions.findByEndpoint(request.endpoint())
                .filter(s -> s.getProfile().getAccount().getId().equals(accountId))
                .orElseThrow(() -> new CustomException(PushErrorCode.SUBSCRIPTION_NOT_FOUND));
        subscriptions.delete(subscription);
    }

    // 발송 시점이 아니라 등록 시점에 잘못된 키를 거름
    private static void validateKeys(PushDtos.Keys keys) {
        try {
            WebPushCrypto.publicKey(WebPushCrypto.decode(keys.p256dh()));
            if (WebPushCrypto.decode(keys.auth()).length != 16) {
                throw new IllegalArgumentException("auth 길이");
            }
        } catch (Exception e) {
            throw new CustomException(PushErrorCode.INVALID_SUBSCRIPTION_KEYS);
        }
    }
}
