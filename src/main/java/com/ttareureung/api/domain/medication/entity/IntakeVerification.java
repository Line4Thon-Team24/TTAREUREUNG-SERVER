package com.ttareureung.api.domain.medication.entity;

// 복용 처리 근거. 보호자 확인 화면용
public enum IntakeVerification {
    VISION,  // 약 사진 판독 MATCH 후 복용
    MANUAL   // 판독 없이 또는 판독 실패 후 시니어가 직접 선택
}
