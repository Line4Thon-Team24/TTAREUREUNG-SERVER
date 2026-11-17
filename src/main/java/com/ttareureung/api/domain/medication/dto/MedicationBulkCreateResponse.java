package com.ttareureung.api.domain.medication.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record MedicationBulkCreateResponse(
        @JsonProperty("medication_ids") List<Long> medicationIds, String message) {

}
