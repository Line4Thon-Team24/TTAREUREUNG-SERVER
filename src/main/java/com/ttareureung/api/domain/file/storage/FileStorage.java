package com.ttareureung.api.domain.file.storage;

import java.time.Duration;

/**
 * 사진 저장소. 약·처방전 사진은 개인정보라 공개 URL 대신 시간제한 링크로만 노출
 * 구현: S3(운영), 서버 디스크(로컬·테스트)
 */
public interface FileStorage {

    void store(String key, byte[] data, String contentType);

    // ttl 동안만 열리는 조회 링크
    String url(String key, Duration ttl);
}
