package com.ttareureung.api.domain.medication.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record SeniorMedicationSummaryResponse(List<Item> seniors) {
    public record Item(@JsonProperty("senior_id") Long seniorId,
                       boolean registered,
                       @JsonProperty("medication_count") long medicationCount) {}
}
