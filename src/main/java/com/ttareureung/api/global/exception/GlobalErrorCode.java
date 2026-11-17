
package com.ttareureung.api.global.exception;

import org.springframework.http.HttpStatus;

public enum GlobalErrorCode implements ErrorCode {

    INVALID_INPUT(
            HttpStatus.BAD_REQUEST,
            "COMMON_400",
            "잘못된 요청입니다."
    ),

    UNAUTHORIZED(
            HttpStatus.UNAUTHORIZED,
            "COMMON_401",
            "인증이 필요합니다."
    ),

    FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "COMMON_403",
            "접근 권한이 없습니다."
    ),

    NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "COMMON_404",
            "요청한 리소스를 찾을 수 없습니다."
    ),

    CONFLICT(
            HttpStatus.CONFLICT,
            "COMMON_409",
            "요청이 현재 데이터 상태와 충돌합니다."
    ),

    UNSUPPORTED_MEDIA_TYPE(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "COMMON_415",
            "지원하지 않는 요청 형식입니다."
    ),

    INTERNAL_SERVER_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "COMMON_500",
            "서버 내부 오류가 발생했습니다."
    ),

    SERVICE_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "COMMON_503",
            "일시적으로 서비스를 이용할 수 없습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;

    GlobalErrorCode(
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
