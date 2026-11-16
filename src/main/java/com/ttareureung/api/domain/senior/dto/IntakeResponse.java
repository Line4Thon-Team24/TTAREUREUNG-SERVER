package com.ttareureung.api.domain.senior.dto;

import com.ttareureung.api.domain.medication.entity.IntakeVerification;
import com.ttareureung.api.domain.medication.entity.MedicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// "다 먹었어" 처리 결과. nextMedication이 null이면 회차의 약을 다 먹은 것 → finish 호출
public record IntakeResponse(
        @Schema(example = "15") Long sessionId,
        @Schema(example = "7") Long medicationId,
        MedicationStatus status,
        @Schema(example = "2026-11-08T15:12:00") LocalDateTime takenAt,
        @Schema(description = "VISION: 판독 MATCH 후 복용, MANUAL: 직접 선택") IntakeVerification verification,
        @Schema(description = "아직 안 먹은 약 수", example = "1") int remainingCount,
        @Schema(example = "3") int totalCount,
        @Schema(description = "다음에 찾을 약. 없으면 null") MedicationGuide nextMedication,
        @Schema(description = "TTS로 읽을 문장") String voiceText
) {
}
