package com.ttareureung.api.domain.file.storage;

import com.ttareureung.api.domain.file.exception.FileErrorCode;
import com.ttareureung.api.global.exception.CustomException;
import com.ttareureung.api.global.security.JwtProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * 서버 디스크 저장소. S3 키가 없는 로컬·테스트 환경용
 * S3 서명 URL과 같은 동작을 위해 HMAC 서명·만료 시각이 붙은 링크로만 조회
 */
@Component
public class LocalFileStorage implements FileStorage {

    private final Path root;
    private final String publicBaseUrl;
    private final byte[] secret;
    private final Clock clock;

    public LocalFileStorage(StorageProperties properties, JwtProperties jwtProperties, Clock clock) {
        this.root = Path.of(properties.local().dir()).toAbsolutePath().normalize();
        this.publicBaseUrl = properties.publicBaseUrl().replaceAll("/+$", "");
        // 별도 비밀값 없이 JWT 비밀키 재사용. 링크 위조 방지 용도라 충분
        this.secret = jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    @Override
    public void store(String key, byte[] data, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, data);
        } catch (IOException e) {
            throw new CustomException(FileErrorCode.STORAGE_FAILED);
        }
    }

    @Override
    public String url(String key, Duration ttl) {
        long exp = clock.instant().plus(ttl).getEpochSecond();
        return publicBaseUrl + "/api/v1/files/local/" + key + "?exp=" + exp + "&sig=" + sign(key, exp);
    }

    // 서명·만료 확인 후 파일 읽기. 조회 엔드포인트 전용
    public byte[] load(String key, long exp, String sig) {
        if (sig == null || exp < clock.instant().getEpochSecond()
                || !MessageDigest.isEqual(sign(key, exp).getBytes(StandardCharsets.UTF_8), sig.getBytes(StandardCharsets.UTF_8))) {
            throw new CustomException(FileErrorCode.LINK_INVALID);
        }
        Path target = resolve(key);
        if (!Files.isRegularFile(target)) {
            throw new CustomException(FileErrorCode.FILE_NOT_FOUND);
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw new CustomException(FileErrorCode.FILE_NOT_FOUND);
        }
    }

    // 키에 ".."이 섞여 저장 폴더 밖으로 나가는 것 차단
    private Path resolve(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new CustomException(FileErrorCode.FILE_NOT_FOUND);
        }
        return target;
    }

    private String sign(String key, long exp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] digest = mac.doFinal((key + "|" + exp).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Optional<String> contentTypeOf(String key) {
        String lower = key.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return Optional.of("image/jpeg");
        if (lower.endsWith(".png")) return Optional.of("image/png");
        if (lower.endsWith(".webp")) return Optional.of("image/webp");
        if (lower.endsWith(".pdf")) return Optional.of("application/pdf");
        return Optional.empty();
    }
}
