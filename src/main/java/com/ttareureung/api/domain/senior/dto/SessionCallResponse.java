package com.ttareureung.api.domain.senior.dto;

import com.ttareureung.api.domain.medication.entity.MealTimeSlot;
import com.ttareureung.api.domain.senior.entity.DoseSessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

// 수신화면과 전화 받기 응답 공용. medications는 아직 안 먹은 약만, 순서는 약 등록 순
public record SessionCallResponse(
        @Schema(example = "15") Long sessionId,
        MealTimeSlot timeSlot,
        @Schema(example = "점심 식후") String slotName,
        @Schema(example = "2026-11-08T15:00:00") LocalDateTime scheduledAt,
        DoseSessionStatus status,
        @Schema(description = "미응답 횟수", example = "0") int callAttempts,
        @Schema(description = "회차 전체 약 수", example = "3") int totalCount,
        @Schema(description = "TTS로 읽을 인사말") String voiceText,
        List<MedicationGuide> medications
) {
}
