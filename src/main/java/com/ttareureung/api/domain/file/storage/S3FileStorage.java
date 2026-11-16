package com.ttareureung.api.domain.file.storage;

import com.ttareureung.api.domain.file.exception.FileErrorCode;
import com.ttareureung.api.global.exception.CustomException;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;

/**
 * S3 저장소. 버킷은 비공개, 조회는 서명 URL
 * 키를 비우면 EC2 역할 등 기본 자격 증명 체인 사용
 */
@Slf4j
public class S3FileStorage implements FileStorage {

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;

    public S3FileStorage(StorageProperties.S3 props) {
        Region region = Region.of(props.region());
        AwsCredentialsProvider credentials = hasText(props.accessKey()) && hasText(props.secretKey())
                ? StaticCredentialsProvider.create(AwsBasicCredentials.create(props.accessKey(), props.secretKey()))
                : DefaultCredentialsProvider.create();
        this.client = S3Client.builder().region(region).credentialsProvider(credentials).build();
        this.presigner = S3Presigner.builder().region(region).credentialsProvider(credentials).build();
        this.bucket = props.bucket();
    }

    @Override
    public void store(String key, byte[] data, String contentType) {
        try {
            client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                    RequestBody.fromBytes(data));
        } catch (RuntimeException e) {
            log.error("S3 업로드 실패 key={}", key, e);
            throw new CustomException(FileErrorCode.STORAGE_FAILED);
        }
    }

    @Override
    public String url(String key, Duration ttl) {
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(ttl)
                        .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                        .build())
                .url().toString();
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
