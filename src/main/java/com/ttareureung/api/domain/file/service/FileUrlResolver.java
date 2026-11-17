package com.ttareureung.api.domain.file.service;

import com.ttareureung.api.domain.file.storage.FileStorage;
import com.ttareureung.api.domain.file.storage.StorageProperties;
import org.springframework.stereotype.Component;

/**
 * 약의 image_url 값을 화면에서 열 수 있는 링크로 변환
 * 업로드 API로 받은 키(uploads/...)는 서명 URL로, 외부 http 주소는 그대로
 */
@Component
public class FileUrlResolver {

    private final FileStorage storage;
    private final StorageProperties properties;

    public FileUrlResolver(FileStorage storage, StorageProperties properties) {
        this.storage = storage;
        this.properties = properties;
    }

    public String resolve(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        if (stored.startsWith("http://") || stored.startsWith("https://")) {
            return stored;
        }
        return storage.url(stored, properties.urlTtl());
    }
}
