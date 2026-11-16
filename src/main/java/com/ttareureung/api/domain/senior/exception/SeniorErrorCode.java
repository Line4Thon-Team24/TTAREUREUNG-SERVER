package com.ttareureung.api.domain.senior.exception;

import com.ttareureung.api.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SeniorErrorCode implements ErrorCode {

    // 회차 시각이 뒤섞이면 다음 전화 계산이 어긋남
    DOSE_TIME_ORDER_INVALID(
            HttpStatus.BAD_REQUEST,
            "SENIOR_001",
            "복용 시각은 아침, 점심, 저녁, 밤 순서여야 합니다."
    ),

    DUPLICATE_TIME_SLOT(
            HttpStatus.BAD_REQUEST,
            "SENIOR_002",
            "같은 회차가 중복되었습니다."
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
