package com.ttareureung.api.domain.senior.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ttareureung.api.domain.medication.entity.MealTimeSlot;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;
import java.util.List;

// 보낸 회차만 변경, 나머지 회차는 유지
public record DoseTimesUpdateRequest(
        @NotEmpty @Valid List<Item> doseTimes
) {
    public record Item(
            @Schema(example = "MORNING") @NotNull MealTimeSlot timeSlot,
            @Schema(example = "09:00", type = "string") @NotNull @JsonFormat(pattern = "HH:mm") LocalTime doseTime
    ) {
    }
}
