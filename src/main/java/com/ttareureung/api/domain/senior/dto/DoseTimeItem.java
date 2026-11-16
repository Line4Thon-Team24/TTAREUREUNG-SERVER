package com.ttareureung.api.domain.senior.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ttareureung.api.domain.medication.entity.MealTimeSlot;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalTime;

public record DoseTimeItem(
        @Schema(example = "MORNING") MealTimeSlot timeSlot,
        @Schema(example = "아침 식후") String slotName,
        @Schema(example = "08:00", type = "string") @JsonFormat(pattern = "HH:mm") LocalTime doseTime
) {
    public static DoseTimeItem of(MealTimeSlot slot, LocalTime time) {
        return new DoseTimeItem(slot, slot.getLabel(), time);
    }
}
