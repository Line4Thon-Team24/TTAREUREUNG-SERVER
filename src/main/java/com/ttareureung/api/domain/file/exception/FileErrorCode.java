package com.ttareureung.api.domain.file.exception;

import com.ttareureung.api.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum FileErrorCode implements ErrorCode {

    FILE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "FILE_001",
            "파일을 찾을 수 없습니다."
    ),

    // 서명이 틀리거나 만료된 링크. 사진 URL은 1시간 뒤 다시 조회해야 함
    LINK_INVALID(
            HttpStatus.FORBIDDEN,
            "FILE_002",
            "사진 링크가 만료되었습니다. 다시 불러와주세요."
    ),

    STORAGE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "FILE_003",
            "사진 저장에 실패했습니다. 잠시 후 다시 시도해주세요."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;

    FileErrorCode(
            HttpStatus status,
            String code,
            String message
    ) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
