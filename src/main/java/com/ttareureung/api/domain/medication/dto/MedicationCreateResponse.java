package com.ttareureung.api.domain.medication.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MedicationCreateResponse(@JsonProperty("medication_id") Long medicationId, String message) {

}

