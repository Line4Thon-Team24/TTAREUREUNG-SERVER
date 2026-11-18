package com.ttareureung.api.domain.medication.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ttareureung.api.domain.medication.entity.CycleType;
import com.ttareureung.api.domain.medication.entity.IntakeTiming;
import com.ttareureung.api.domain.medication.entity.MealTimeSlot;
import com.ttareureung.api.domain.medication.entity.RegistrationType;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

public record MedicationCreateRequest(
        @JsonProperty("senior_profile_id") @NotNull Long seniorProfileId,
        @JsonProperty("drug_name") @NotBlank @Size(max = 100) String drugName,
        @JsonProperty("custom_drug_name") @Size(max = 100) String customDrugName,
        @Size(max = 50) String category,
        @JsonProperty("image_url") @Size(max = 255) String imageUrl,
        @JsonProperty("prescription_file_name") @Size(max = 255) String prescriptionFileName,
        @JsonProperty("registration_type") RegistrationType registrationType,
        String effect,
        @JsonProperty("storage_location") @NotBlank @Size(max = 100) String storageLocation,
        @JsonProperty("dose_amount") @Size(max = 20) String doseAmount,
        @Size(max = 50) String appearance,
        @Size(max = 30) String strength,
        @JsonProperty("intake_reason") @Size(max = 1000) String intakeReason,
        @JsonProperty("start_date") @NotNull LocalDate startDate,
        @JsonProperty("end_date") @NotNull LocalDate endDate,
        @JsonProperty("cycle_type") @NotNull CycleType cycleType,
        @JsonProperty("interval_days") @Min(1) @Max(30) Integer intervalDays,
        @JsonProperty("intake_timing") IntakeTiming intakeTiming,
        @Size(max = 100) String frequency,
        @JsonProperty("notify_on_miss") @NotNull Boolean notifyOnMiss,
        @JsonProperty("meal_time_slots") @NotEmpty List<MealTimeSlot> mealTimeSlots
) {
    public MedicationItemRequest toItem() {
        return new MedicationItemRequest(drugName, customDrugName, category, imageUrl, prescriptionFileName,
                registrationType, effect, storageLocation, doseAmount, appearance, strength, intakeReason, startDate,
                endDate, cycleType, intervalDays, intakeTiming, frequency, notifyOnMiss, mealTimeSlots);
    }
}
