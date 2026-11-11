package com.ttareureung.api.domain.profile.exception;

import com.ttareureung.api.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ProfileErrorCode implements ErrorCode {

    // 가입 흐름상 보호자 등록이 먼저라 보호자 없는 계정의 시니어 생성 차단
    GUARDIAN_REQUIRED(
            HttpStatus.CONFLICT,
            "PROFILE_001",
            "보호자 프로필을 먼저 등록해주세요."
    ),

    // 다른 계정 프로필도 404 처리. 403이면 ID 존재 여부가 드러남
    PROFILE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "PROFILE_002",
            "프로필을 찾을 수 없습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;

    ProfileErrorCode(
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
