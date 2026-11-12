package com.ttareureung.api.domain.medication.service;

import com.ttareureung.api.domain.medication.dto.*;
import com.ttareureung.api.domain.medication.dto.DailyScheduleResponse.Medicine;
import com.ttareureung.api.domain.medication.dto.DailyScheduleResponse.TimeSlot;
import com.ttareureung.api.domain.medication.entity.MealTimeSlot;
import com.ttareureung.api.domain.medication.entity.Medication;
import com.ttareureung.api.domain.medication.entity.MedicationLog;
import com.ttareureung.api.domain.medication.entity.MedicationStatus;
import com.ttareureung.api.domain.medication.exception.MedicationErrorCode;
import com.ttareureung.api.domain.medication.repository.MedicationLogRepository;
import com.ttareureung.api.domain.medication.repository.MedicationRepository;
import com.ttareureung.api.domain.profile.entity.Profile;
import com.ttareureung.api.domain.profile.entity.ProfileRole;
import com.ttareureung.api.domain.profile.repository.ProfileRepository;
import com.ttareureung.api.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MedicationService {

    private static final long MAX_PERIOD_DAYS = 366;  // 1년으로 가정

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
                .orElseThrow(() -> new CustomException(MedicationErrorCode.MEDICATION_LOG_NOT_FOUND));

        log.changeStatus(status);
        return new UpdateLogStatusResponse(log.getId(), log.getStatus(), "복용 상태가 변경되었습니다.");
    }

    @Transactional
    public MedicationCreateResponse register(MedicationCreateRequest req) {
        validateSenior(req.seniorProfileId());
        Long id = createOne(req.seniorProfileId(), req.toItem());
        return new MedicationCreateResponse(id, "약 복용 일정이 등록되어 있습니다.");
    }

    @Transactional // 하나라도 실패하면 전체 롤백
    public MedicationBulkCreateResponse registerBulk(MedicationBulkCreateRequest req) {
        validateSenior(req.seniorProfileId());
        List<Long> ids = req.medications().stream()
                .map(item -> createOne(req.seniorProfileId(), item))
                .toList();
        return new MedicationBulkCreateResponse(ids, ids.size() + "개의 약 복용 일정이 등록되었습니다.");
    }

    @Transactional
    public MedicationBulkDeleteResponse deleteMedications(List<Long> medicationIds) {
        List<Long> ids = medicationIds.stream().distinct().toList();
        if (medicationRepository.findAllById(ids).size() != ids.size()) {
            throw new CustomException(MedicationErrorCode.MEDICATION_NOT_FOUND);
        }
        medicationLogRepository.deleteByMedicationIds(ids); // FK 때문에 로그 먼저
        medicationRepository.deleteAllByIdInBatch(ids);
        return new MedicationBulkDeleteResponse(ids.size(), ids.size() + "개의 약 복용 일정이 삭제되었습니다.");
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
    public void deleteMedication(Long medicationId) {
        Medication m = findMedication(medicationId);
        medicationLogRepository.deleteByMedicationIds(medicationId);
        medicationRepository.delete(m);
        // return 없음 (void)
    }

    private void validateSenior(Long seniorProfileId) {
        Profile senior = profileRepository.findById(seniorProfileId)
                .orElseThrow(() -> new CustomException(MedicationErrorCode.SENIOR_NOT_FOUND));
        if (senior.getRole() != ProfileRole.SENIOR) {
            throw new CustomException(MedicationErrorCode.NOT_SENIOR_PROFILE);
        }
    }

    private Long createOne(Long seniorProfileId, MedicationItemRequest item) {
        if (item.endDate().isBefore(item.startDate())) {
            throw new CustomException(MedicationErrorCode.INVALID_DATE_RANGE);
        }
        if (ChronoUnit.DAYS.between(item.startDate(), item.endDate()) > MAX_PERIOD_DAYS) {
            throw new CustomException(MedicationErrorCode.PERIOD_TOO_LONG);
        }

        Medication medication = medicationRepository.save(Medication.builder()
                .seniorProfileId(seniorProfileId)
                .drugName(item.drugName())
                .customDrugName(item.customDrugName())
                .imageUrl(item.imageUrl())
                .effect(item.effect())
                .startDate(item.startDate())
                .endDate(item.endDate())
                .cycleType(item.cycleType())
                .frequency(item.frequency())
                .notifyOnMiss(item.notifyOnMiss())
                .build());

        List<MealTimeSlot> slots = item.mealTimeSlots().stream().distinct().toList();
        List<MedicationLog> logs = new ArrayList<>();
        for (LocalDate d = item.startDate(); !d.isAfter(item.endDate()); d = d.plusDays(1)) {
            for (MealTimeSlot slot : slots) {
                logs.add(MedicationLog.create(medication, d, slot));
            }
        }
        medicationLogRepository.saveAll(logs);
        return medication.getId();
    }

    private Medication findMedication(Long id) {
        return medicationRepository.findById(id)
                .orElseThrow(() -> new CustomException(MedicationErrorCode.MEDICATION_NOT_FOUND));
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