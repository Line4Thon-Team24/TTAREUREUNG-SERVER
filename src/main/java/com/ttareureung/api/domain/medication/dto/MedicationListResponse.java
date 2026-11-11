package com.ttareureung.api.domain.medication.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

public record MedicationListResponse(List<MedicationItem> medications) {
    public record MedicationItem(
            @JsonProperty("medication_id") Long medicationId,
            @JsonProperty("drug_name") String drugName,
            @JsonProperty("custom_drug_name") String customDrugName,
            @JsonProperty("image_url") String imageUrl,
            String effect,
            String frequency,
            @JsonProperty("start_date") LocalDate startDate,
            @JsonProperty("end_date") LocalDate endDate
    ) {

    }
    
}
