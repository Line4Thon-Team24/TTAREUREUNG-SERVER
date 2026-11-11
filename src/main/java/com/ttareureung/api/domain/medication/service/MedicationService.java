package com.ttareureung.api.domain.medication.service;

import com.ttareureung.api.domain.medication.dto.*;
import com.ttareureung.api.domain.medication.dto.DailyScheduleResponse.Medicine;
import com.ttareureung.api.domain.medication.dto.DailyScheduleResponse.TimeSlot;
import com.ttareureung.api.domain.medication.entity.MealTimeSlot;
import com.ttareureung.api.domain.medication.entity.Medication;
import com.ttareureung.api.domain.medication.entity.MedicationLog;
import com.ttareureung.api.domain.medication.entity.MedicationStatus;
import com.ttareureung.api.domain.medication.repository.MedicationLogRepository;
import com.ttareureung.api.domain.medication.repository.MedicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MedicationService {

    private final MedicationLogRepository medicationLogRepository;
    private final MedicationRepository medicationRepository;
    private final ProfileRepository profileRepository;

    @Transactional(readOnly = true)
    public DailyScheduleResponse getDailySchedule(Long seniorId, LocalDate date) {
        List<MedicationLog> logs = medicationLogRepository.findDailyLogs(seniorId, date);

        // enum
        Map<MealTimeSlot, List<MedicationLog>> grouped = logs.stream()
                .collect(Collectors.groupingBy(MedicationLog::getMealTimeSlot, TreeMap::new, Collectors.toList()));

        List<TimeSlot> timeSlots = grouped.entrySet().stream()
                .map(e -> toTimeSlot(e.getKey(), e.getValue()))
                .toList();

        return new DailyScheduleResponse(date, timeSlots);
    }

    @Transactional
    public UpdateLogStatusResponse updateLogStatus(Long logId, MedicationStatus status) {
        MedicationLog log = medicationLogRepository.findById(logId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "복용 기록을 찾을 수 없습니다."));

        log.changeStatus(status); 
        return new UpdateLogStatusResponse(log.getId(), log.getStatus(), "복용 상태가 변경되었습니다.");
    }

    @Transactional
    public MedicationCreateResponse register(MedicationCreateRequest req) {
        Profile senior = profileRepository.findById(req.seniorProfileId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "시니어를 찾을 수 없습니다."));
        if (senior.getRole() != ProfileRole.SENIOR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "시니어 프로필에만 약을 등록할 수 있습니다.");
        }
        if (req.endDate().isBefore(req.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "종료일은 시작일 이후여야 합니다.");
        }

        Medication medication = medicationRepository.save(Medication.builder()
                .seniorProfileId(req.seniorProfileId())
                .drugName(req.drugName())
                .customDrugName(req.customDrugName())
                .imageUrl(req.imageUrl())
                .effect(req.effect())
                .startDate(req.startDate())
                .endDate(req.endDate())
                .cycleType(req.cycleType())
                .frequency(req.frequency())
                .notifyOnMiss(req.notifyOnMiss())
                .build());

        // 복용 로그 생성
        List<MealTimeSlot> slots = req.mealTimeSlots().stream().distinct().toList();
        List<MedicationLog> logs = new ArrayList<>();
        for (LocalDate d = req.startDate(); !d.isAfter(req.endDate()); d = d.plusDays(1)) {
            for (MealTimeSlot slot : slots) {
                logs.add(MedicationLog.create(medication, d, slot));
            }
        }
        medicationLogRepository.saveAll(logs);

        return new MedicationCreateResponse(medication.getId(), "약 복용 일정이 등록되어 있습니다.");
    }

    @Transactional(readOnly = true)
    public MedicationListResponse getMedications(Long seniorId) {
        return new MedicationListResponse(
                medicationRepository.findBySeniorProfileIdOrderByCreatedAtDesc(seniorId).stream()
                        .map(m -> new MedicationListResponse.MedicationItem(
                                m.getId(), m.getDrugName(), m.getCustomDrugName(), m.getImageUrl(),
                                m.getEffect(), m.getFrequency(), m.getStartDate(), m.getEndDate()))
                        .toList());
    }

    @Transactional
    public MedicationUpdateResponse updateMedication(Long medicationId, MedicationUpdateRequest req) {
        Medication m = findMedication(medicationId);
        m.update(req.customDrugName(), req.frequency(), req.effect(), req.imageUrl(), req.notifyOnMiss());
        return new MedicationUpdateResponse(m.getId(), "약 정보가 수정되었습니다.");
    }

    @Transactional
    public MessageResponse deleteMedication(Long medicationId) {
        Medication m = findMedication(medicationId);
        medicationLogRepository.deleteByMedicationId(medicationId);
        medicationRepository.delete(m);
        return new MessageResponse("약 복용 일정이 삭제되었습니다.");
    }

    private Medication findMedication(Long id) {
        return medicationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "약 일정을 찾을 수 없습니다."));
    }

    private TimeSlot toTimeSlot(MealTimeSlot slot, List<MedicationLog> logs) {
        List<Medicine> medicines = logs.stream()
                .map(l -> new Medicine(l.getId(), l.getMedication().getId(),
                        l.getMedication().getDisplayName(), l.getStatus()))
                .toList();

        return new TimeSlot(slot.name(), slot.getLabel(), calcSlotStatus(logs), medicines);
    }

    private String calcSlotStatus(List<MedicationLog> logs) {
        long taken = logs.stream().filter(l -> l.getStatus() == MedicationStatus.TAKEN).count();
        long missed = logs.stream().filter(l -> l.getStatus() == MedicationStatus.MISSED).count();

        if (taken == logs.size()) return "TAKEN";
        if (missed == logs.size()) return "MISSED";
        if (taken > 0) return "PARTIAL";
        return "PENDING";
    }
}