package com.ttareureung.api.domain.medication.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MedicationBulkDeleteResponse(
        @JsonProperty("deleted_count") int deletedCount, String message) {

}
