
package com.ttareureung.api.domain.auth.exception;

import com.ttareureung.api.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AuthErrorCode implements ErrorCode {

    INVALID_CREDENTIALS(
            HttpStatus.UNAUTHORIZED,
            "AUTH_001",
            "이메일 또는 비밀번호가 올바르지 않습니다."
    ),

    EMAIL_DUPLICATED(
            HttpStatus.CONFLICT,
            "AUTH_002",
            "이미 가입된 이메일입니다."
    ),

    TOKEN_EXPIRED(
            HttpStatus.UNAUTHORIZED,
            "AUTH_003",
            "로그인 정보가 만료되었습니다."
    ),

    INVALID_TOKEN(
            HttpStatus.UNAUTHORIZED,
            "AUTH_004",
            "유효하지 않은 인증 정보입니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;

    AuthErrorCode(
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
