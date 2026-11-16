package com.ttareureung.api.domain.file.service;

import com.ttareureung.api.domain.file.dto.FileUploadResponse;
import com.ttareureung.api.domain.file.exception.FileErrorCode;
import com.ttareureung.api.domain.file.storage.FileStorage;
import com.ttareureung.api.domain.file.storage.StorageProperties;
import com.ttareureung.api.domain.medication.exception.MedicationErrorCode;
import com.ttareureung.api.domain.profile.service.ProfileAccessChecker;
import com.ttareureung.api.domain.senior.service.SeniorTime;
import com.ttareureung.api.global.exception.CustomException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * 사진 업로드. 파일 이름은 버리고 날짜 폴더 + 무작위 키로 저장(이름 충돌·추측 방지)
 */
@Service
public class FileService {

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "application/pdf", "pdf");

    private final ProfileAccessChecker accessChecker;
    private final FileStorage storage;
    private final StorageProperties properties;
    private final Clock clock;

    public FileService(ProfileAccessChecker accessChecker, FileStorage storage,
                       StorageProperties properties, Clock clock) {
        this.accessChecker = accessChecker;
        this.storage = storage;
        this.properties = properties;
        this.clock = clock;
    }

    public FileUploadResponse upload(Long accountId, MultipartFile file) {
        accessChecker.activeAccount(accountId);
        String contentType = file.getContentType();
        String extension = contentType == null ? null : EXTENSIONS.get(contentType);
        if (file.isEmpty() || extension == null) {
            throw new CustomException(MedicationErrorCode.UNSUPPORTED_FILE_TYPE);
        }
        LocalDateTime now = LocalDateTime.now(clock.withZone(SeniorTime.ZONE));
        String key = "uploads/" + now.format(DateTimeFormatter.ofPattern("yyyy/MM")) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + extension;
        try {
            storage.store(key, file.getBytes(), contentType);
        } catch (IOException e) {
            throw new CustomException(FileErrorCode.STORAGE_FAILED);
        }
        return new FileUploadResponse(key, storage.url(key, properties.urlTtl()), contentType, file.getSize(),
                now.plus(properties.urlTtl()));
    }
}
