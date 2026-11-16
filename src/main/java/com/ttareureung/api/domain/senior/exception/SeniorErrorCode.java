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
    ),

    // 접근 권한 없는 회차도 404. 403이면 회차 ID 존재 여부가 드러남
    SESSION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "SENIOR_003",
            "회차를 찾을 수 없습니다."
    ),

    SESSION_FINISHED(
            HttpStatus.CONFLICT,
            "SENIOR_004",
            "이미 끝난 회차입니다."
    ),

    // 전화 받은 뒤(IN_PROGRESS)에는 미응답 처리 불가
    SESSION_NOT_WAITING(
            HttpStatus.CONFLICT,
            "SENIOR_005",
            "전화 대기 중인 회차가 아닙니다."
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
