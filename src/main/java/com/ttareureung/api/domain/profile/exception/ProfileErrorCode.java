package com.ttareureung.api.domain.profile.exception;

import com.ttareureung.api.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ProfileErrorCode implements ErrorCode {

    // 회원가입 때 보호자를 먼저 등록하는 흐름이라, 보호자 없는 계정에 시니어가 생기지 않게 막는다.
    GUARDIAN_REQUIRED(
            HttpStatus.CONFLICT,
            "PROFILE_001",
            "보호자 프로필을 먼저 등록해주세요."
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
