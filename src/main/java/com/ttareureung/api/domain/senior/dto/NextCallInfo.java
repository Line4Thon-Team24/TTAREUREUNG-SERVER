package com.ttareureung.api.domain.senior.dto;

import com.ttareureung.api.domain.medication.entity.MealTimeSlot;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

// "다음 알림 전화 오후 6시 (혈압약 · 소화제)"
public record NextCallInfo(
        @Schema(example = "2026-11-08T18:00:00") LocalDateTime scheduledAt,
        MealTimeSlot timeSlot,
        @Schema(example = "저녁 식후") String slotName,
        List<String> medicationNames
) {
}
