package com.ttareureung.api.domain.medication.dto;

import com.ttareureung.api.domain.medication.entity.MedicationStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateLogStatusRequest(@NotNull MedicationStatus status) {

}
