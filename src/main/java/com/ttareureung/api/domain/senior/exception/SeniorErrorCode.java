
package com.ttareureung.api.domain.senior.exception;

import com.ttareureung.api.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SeniorErrorCode implements ErrorCode {

    SENIOR_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "SENIOR_001",
            "시니어 정보를 찾을 수 없습니다."
    ),

    ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "SENIOR_002",
            "해당 시니어에 대한 접근 권한이 없습니다."
    ),

    ALREADY_REGISTERED(
            HttpStatus.CONFLICT,
            "SENIOR_003",
            "이미 등록된 시니어입니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;

    SeniorErrorCode(
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
