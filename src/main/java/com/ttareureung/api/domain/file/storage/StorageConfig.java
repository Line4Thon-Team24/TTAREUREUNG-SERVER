package com.ttareureung.api.domain.file.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

// 환경변수 AWS_S3_BUCKET 유무로 저장소 선택. 로컬 디스크 빈은 조회 엔드포인트용으로 항상 등록
@Slf4j
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean
    @Primary
    public FileStorage fileStorage(StorageProperties properties, LocalFileStorage local) {
        if (properties.s3().enabled()) {
            log.info("사진 저장소: S3 bucket={}", properties.s3().bucket());
            return new S3FileStorage(properties.s3());
        }
        log.info("사진 저장소: 서버 디스크 dir={}", properties.local().dir());
        return local;
    }
}
