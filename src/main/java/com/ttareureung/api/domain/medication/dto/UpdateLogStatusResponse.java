package com.ttareureung.api.domain.medication.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ttareureung.api.domain.medication.entity.MedicationStatus;

public class UpdateLogStatusResponse(@JsonProperty("log_id") Long logId, MedicationStatus status, String message) {

}
