package com.ttareureung.api.domain.medication.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ttareureung.api.domain.medication.entity.CycleType;
import com.ttareureung.api.domain.medication.entity.IntakeTiming;
import com.ttareureung.api.domain.medication.entity.MealTimeSlot;
import com.ttareureung.api.domain.medication.entity.RegistrationType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record MedicationUpdateRequest(
        @JsonProperty("drug_name") @Pattern(regexp = ".*\\S.*") @Size(max = 100) String drugName,
        @JsonProperty("custom_drug_name") @Size(max = 100) String customDrugName,
        @Size(max = 50) String category,                                         // 빈 문자열이면 삭제
        @JsonProperty("registration_type") RegistrationType registrationType,
        @Size(max = 100) String frequency,
        String effect,
        @JsonProperty("image_url") @Size(max = 255) String imageUrl,
        @JsonProperty("notify_on_miss") Boolean notifyOnMiss,
        @JsonProperty("storage_location") @Pattern(regexp = ".*\\S.*") @Size(max = 100) String storageLocation,
        @JsonProperty("dose_amount") @Size(max = 20) String doseAmount,
        @Size(max = 50) String appearance,
        @Size(max = 30) String strength,
        @JsonProperty("intake_reason") @Size(max = 1000) String intakeReason,
        @JsonProperty("start_date") LocalDate startDate,
        @JsonProperty("end_date") LocalDate endDate,
        @JsonProperty("cycle_type") CycleType cycleType,
        @JsonProperty("interval_days") @Min(1) @Max(30) Integer intervalDays,
        @JsonProperty("intake_timing") IntakeTiming intakeTiming,
        @JsonProperty("meal_time_slots") @Size(min = 1) List<MealTimeSlot> mealTimeSlots
) {
    
}
