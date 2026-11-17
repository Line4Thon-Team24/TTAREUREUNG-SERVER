package com.ttareureung.api.domain.senior.dto;

import com.ttareureung.api.domain.medication.entity.MealTimeSlot;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// "김순자님께서 10분 후 오후 3시 약 복용 예정입니다." 저장하지 않고 조회 시 계산
public record UpcomingCall(
        @Schema(example = "2") Long seniorId,
        @Schema(example = "김순자") String seniorName,
        @Schema(example = "15") Long sessionId,
        MealTimeSlot timeSlot,
        @Schema(example = "점심 식후") String slotName,
        @Schema(example = "2026-11-08T15:00:00") LocalDateTime scheduledAt,
        @Schema(example = "8") int minutesLeft
) {
}
