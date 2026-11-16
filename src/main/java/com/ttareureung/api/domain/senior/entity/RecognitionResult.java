package com.ttareureung.api.domain.senior.entity;

// 약 판독 결과. 확실하지 않으면 전부 UNCERTAIN
public enum RecognitionResult {
    MATCH,      // 맞는 약 → 복용 화면
    MISMATCH,   // 다른 약 → 다시 찾기
    UNCERTAIN   // 판독 불가·오류 → 사진 보고 직접 선택
}
