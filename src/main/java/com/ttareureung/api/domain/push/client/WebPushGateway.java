package com.ttareureung.api.domain.push.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;

/**
 * VAPID 서명 + 본문 암호화 후 브라우저 푸시 서비스로 전송.
 * 키는 서버 환경변수에만 보관, 프론트에는 공개키만 노출
 */
@Slf4j
@Component
@EnableConfigurationProperties(PushProperties.class)
public class WebPushGateway implements PushGateway {

    // 받는 쪽이 꺼져 있을 때 푸시 서비스가 보관하는 시간. 복약 알림은 오래 지나면 의미 없음
    private static final int TTL_SECONDS = 600;

    private final PushProperties properties;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final PrivateKey vapidPrivate;

    public WebPushGateway(PushProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        PrivateKey key = null;
        if (properties.enabled()) {
            try {
                key = WebPushCrypto.privateKey(WebPushCrypto.decode(properties.vapidPrivateKey()));
                WebPushCrypto.publicKey(WebPushCrypto.decode(properties.vapidPublicKey()));
            } catch (Exception e) {
                throw new IllegalStateException("VAPID 키 형식이 올바르지 않습니다.", e);
            }
        } else {
            log.info("VAPID 키 미설정. 푸시 발송 비활성화");
        }
        this.vapidPrivate = key;
    }

    @Override
    public boolean enabled() {
        return vapidPrivate != null;
    }

    @Override
    public int send(String endpoint, String p256dh, String auth, byte[] payload) {
        try {
            byte[] body = WebPushCrypto.encrypt(payload, p256dh, auth);
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(10))
                    .header("TTL", String.valueOf(TTL_SECONDS))
                    .header("Urgency", "high")
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("Authorization", "vapid t=" + vapidJwt(endpoint) + ", k=" + properties.vapidPublicKey().trim())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.warn("푸시 발송 실패 status={} body={}", response.statusCode(), response.body());
            }
            return response.statusCode();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        } catch (Exception e) {
            log.warn("푸시 발송 오류: {}", e.toString());
            return -1;
        }
    }

    // aud는 푸시 서비스 출처(scheme://host), 유효 12시간
    private String vapidJwt(String endpoint) throws Exception {
        URI uri = URI.create(endpoint);
        String aud = uri.getScheme() + "://" + uri.getHost();
        long exp = clock.instant().plus(Duration.ofHours(12)).getEpochSecond();
        String subject = properties.subject() == null || properties.subject().isBlank()
                ? "mailto:admin@ttareureung.store" : properties.subject();
        String header = WebPushCrypto.encode("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String claims = WebPushCrypto.encode(("{\"aud\":\"" + aud + "\",\"exp\":" + exp + ",\"sub\":\"" + subject + "\"}")
                .getBytes(StandardCharsets.UTF_8));
        Signature signer = Signature.getInstance("SHA256withECDSAinP1363Format");
        signer.initSign(vapidPrivate);
        signer.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII));
        return header + "." + claims + "." + WebPushCrypto.encode(signer.sign());
    }
}
