package com.ttareureung.api.domain.medication.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ttareureung.api.domain.medication.entity.MedicationStatus;

import java.time.LocalDate;
import java.util.List;

public record DailyScheduleResponse(
        LocalDate date,
        @JsonProperty("time_slots") List<TimeSlot> timeSlots
) {
    public record TimeSlot(
            String slot,
            @JsonProperty("slot_name") String slotName,
            String status,
            List<Medicine> medicines
    ) {}

    public record Medicine(
            @JsonProperty("log_id") Long logId,
            @JsonProperty("medication_id") Long medicationId,
            String name,
            MedicationStatus status
    ) {}
}
