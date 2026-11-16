package com.ttareureung.api.domain.file.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "storage")
public record StorageProperties(
        int urlTtlMinutes,
        String publicBaseUrl,
        S3 s3,
        Local local
) {
    public record S3(String bucket, String region, String accessKey, String secretKey) {
        // 버킷 이름이 있어야 S3 사용. 키는 EC2 역할 등으로 대신할 수 있어 선택
        public boolean enabled() {
            return bucket != null && !bucket.isBlank();
        }
    }

    public record Local(String dir) {
    }

    public Duration urlTtl() {
        return Duration.ofMinutes(urlTtlMinutes <= 0 ? 60 : urlTtlMinutes);
    }
}
