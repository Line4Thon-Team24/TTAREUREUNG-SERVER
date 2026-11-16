package com.ttareureung.api.domain.senior.entity;

// 하루 한 회차의 진행 상태. 일정 화면 표시와 전화 흐름 분기 기준
public enum DoseSessionStatus {
    SCHEDULED,    // 전화 전 또는 재전화 대기
    IN_PROGRESS,  // 전화 받고 약 찾는 중
    COMPLETED,    // 전부 복용
    PARTIAL,      // 일부 복용
    MISSED;       // 재전화까지 실패

    public boolean isFinished() {
        return this == COMPLETED || this == PARTIAL || this == MISSED;
    }
}
