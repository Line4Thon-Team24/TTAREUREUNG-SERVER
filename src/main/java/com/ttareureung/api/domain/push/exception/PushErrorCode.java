package com.ttareureung.api.domain.push.exception;

import com.ttareureung.api.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum PushErrorCode implements ErrorCode {

    SUBSCRIPTION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "PUSH_001",
            "푸시 구독을 찾을 수 없습니다."
    ),

    // 브라우저가 준 p256dh·auth가 아니면 발송 단계에서 암호화 실패
    INVALID_SUBSCRIPTION_KEYS(
            HttpStatus.BAD_REQUEST,
            "PUSH_002",
            "푸시 구독 키 형식이 올바르지 않습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;

    PushErrorCode(HttpStatus status, String code, String message) {
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
