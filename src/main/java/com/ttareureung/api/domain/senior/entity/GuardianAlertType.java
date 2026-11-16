package com.ttareureung.api.domain.senior.entity;

// 보호자 화면 상단 알림 종류. 복용 임박(N분 전)은 저장하지 않고 조회 시 계산
public enum GuardianAlertType {
    COMPLETED,     // 회차 약 전부 복용
    PARTIAL,       // 일부만 복용
    MISSED,        // 미복용 (재전화 대기 포함)
    HELP_REQUEST   // 시니어 도움 요청
}
