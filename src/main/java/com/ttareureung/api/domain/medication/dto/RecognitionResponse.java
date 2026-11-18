package com.ttareureung.api.domain.medication.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ttareureung.api.domain.medication.entity.IntakeTiming;
import com.ttareureung.api.domain.medication.entity.MealTimeSlot;

import java.util.List;

public record RecognitionResponse(
        @JsonProperty("file_name") String fileName,
        List<RecognizedMedication> medications
) {
    public record RecognizedMedication(
            @JsonProperty("drug_name") String drugName,
            String category,
            String effect,
            String frequency,
            @JsonProperty("dose_amount") String doseAmount,
            String strength,
            @JsonProperty("interval_days") Integer intervalDays,
            @JsonProperty("intake_timing") IntakeTiming intakeTiming,
            @JsonProperty("meal_time_slots") List<MealTimeSlot> mealTimeSlots,
            Integer confidence   // 모델 추정값
    ) {}
}
