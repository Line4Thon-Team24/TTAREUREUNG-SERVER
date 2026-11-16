package com.ttareureung.api.domain.senior.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ttareureung.api.domain.medication.entity.MealTimeSlot;
import com.ttareureung.api.domain.medication.entity.MedicationStatus;
import com.ttareureung.api.domain.senior.entity.DoseSessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

// 시니어 "약 복용 일정" 화면 하루치. 회차 순서는 아침 → 점심 → 저녁 → 밤
public record ScheduleResponse(
        @Schema(example = "2026-11-08") LocalDate date,
        @Schema(description = "KST 기준 오늘인지") boolean today,
        @Schema(example = "7") int takenCount,
        @Schema(example = "20") int totalCount,
        List<Session> sessions
) {
    public record Session(
            @Schema(description = "오늘 회차만 있음. 다른 날짜는 null", example = "12") Long sessionId,
            MealTimeSlot timeSlot,
            @Schema(example = "아침 식후") String slotName,
            @Schema(example = "11:00", type = "string") @JsonFormat(pattern = "HH:mm") LocalTime doseTime,
            DoseSessionStatus status,
            @Schema(description = "전화 받은 시각", example = "2026-11-08T11:08:00") LocalDateTime answeredAt,
            List<Medication> medications
    ) {
    }

    public record Medication(
            @Schema(example = "3") Long medicationId,
            @Schema(example = "관절약") String name,
            @Schema(example = "1정") String doseAmount,
            MedicationStatus status
    ) {
    }
}
