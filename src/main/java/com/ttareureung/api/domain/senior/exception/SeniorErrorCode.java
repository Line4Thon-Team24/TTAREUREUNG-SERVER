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
    ),

    MEDICATION_NOT_IN_SESSION(
            HttpStatus.NOT_FOUND,
            "SENIOR_006",
            "이 회차에 없는 약입니다."
    ),

    // 비용·남용 방지. 한도 뒤에는 사진 보고 직접 선택
    RECOGNITION_LIMIT_EXCEEDED(
            HttpStatus.TOO_MANY_REQUESTS,
            "SENIOR_007",
            "판독 횟수를 넘었습니다. 사진을 보고 직접 골라주세요."
    ),

    // 판독·복용은 전화를 받은 뒤(IN_PROGRESS)에만
    SESSION_NOT_IN_PROGRESS(
            HttpStatus.CONFLICT,
            "SENIOR_008",
            "전화를 받은 뒤에 진행할 수 있습니다."
    ),

    IMAGE_TOO_LARGE(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "SENIOR_009",
            "사진은 5MB 이하여야 합니다."
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
