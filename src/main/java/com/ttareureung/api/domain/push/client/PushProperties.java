package com.ttareureung.api.domain.push.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

// VAPID 키(base64url). 둘 다 있어야 발송, 없으면 구독 저장만 하고 발송은 건너뜀
@ConfigurationProperties(prefix = "push")
public record PushProperties(
        String vapidPublicKey,
        String vapidPrivateKey,
        String subject
) {
    public boolean enabled() {
        return vapidPublicKey != null && !vapidPublicKey.isBlank()
                && vapidPrivateKey != null && !vapidPrivateKey.isBlank();
    }
}
