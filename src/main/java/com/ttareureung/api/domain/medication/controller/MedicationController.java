package com.ttareureung.api.domain.medication.controller;

import com.ttareureung.api.domain.medication.dto.DailyScheduleResponse;
import com.ttareureung.api.domain.medication.dto.UpdateLogStatusRequest;
import com.ttareureung.api.domain.medication.dto.UpdateLogStatusResponse;
import com.ttareureung.api.domain.medication.service.MedicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/medications")
@RequiredArgsConstructor
public class MedicationController {

    private final MedicationService medicationService;

    @GetMapping("/schedule")
    public DailyScheduleResponse getDailySchedule(
            @RequestParam("senior_id") Long seniorId,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return medicationService.getDailySchedule(seniorId, date);
    }

    @PatchMapping("/logs/{logId}")
    public UpdateLogStatusResponse updateLogStatus(
            @PathVariable Long logId,
            @RequestBody @Valid UpdateLogStatusRequest request) {
        return medicationService.updateLogStatus(logId, request.status());
    }
}
