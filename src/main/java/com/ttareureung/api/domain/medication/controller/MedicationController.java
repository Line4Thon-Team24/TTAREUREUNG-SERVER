package com.ttareureung.api.domain.medication.controller;

import com.ttareureung.api.domain.medication.dto.*;
import com.ttareureung.api.domain.medication.service.MedicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MedicationCreateResponse register(@RequestBody @Valid MedicationCreateRequest request) {
        return medicationService.register(request);
    }

    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    public MedicationBulkCreateResponse registerBulk(@RequestBody @Valid MedicationBulkCreateRequest request) {
        return medicationService.registerBulk(request);
    }

    @GetMapping
    public MedicationListResponse getMedications(@RequestParam("senior_id") Long seniorId) {
        return medicationService.getMedications(seniorId);
    }

    @PutMapping("/{medicationId}")
    public MedicationUpdateResponse update(@PathVariable Long medicationId,
                                           @RequestBody @Valid MedicationUpdateRequest request) {
        return medicationService.updateMedication(medicationId, request);
    }

    @DeleteMapping("/{medicationId}")
    public MessageResponse delete(@PathVariable Long medicationId) {
        return medicationService.deleteMedication(medicationId);
    }

    @DeleteMapping
    public MedicationBulkDeleteResponse deleteBulk(@RequestParam("medication_ids") List<Long> medicationIds) {
        if (medicationIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "삭제할 약을 선택해주세요.");
        }
        return medicationService.deleteMedications(medicationIds);
    }
    
}
