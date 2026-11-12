package com.ttareureung.api.domain.medication.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ttareureung.api.domain.medication.entity.MealTimeSlot;

import java.util.List;

public record RecognitionResponse(List<RecognizedMedication> medications) {
    public record RecognizedMedication(
            @JsonProperty("drug_name") String drugName,
            String effect,
            String frequency,
            @JsonProperty("meal_time_slots") List<MealTimeSlot> mealTimeSlots,
            Integer confidence   // 모델 추정값
    ) {
        
    }
}
