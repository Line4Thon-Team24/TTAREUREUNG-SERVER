package com.ttareureung.api.domain.medication.service;

import com.ttareureung.api.domain.file.service.FileUrlResolver;
import com.ttareureung.api.domain.medication.dto.*;
import com.ttareureung.api.domain.medication.dto.DailyScheduleResponse.Medicine;
import com.ttareureung.api.domain.medication.dto.DailyScheduleResponse.TimeSlot;
import com.ttareureung.api.domain.medication.entity.*;
import com.ttareureung.api.domain.medication.exception.MedicationErrorCode;
import com.ttareureung.api.domain.medication.repository.MedicationLogRepository;
import com.ttareureung.api.domain.medication.repository.MedicationRepository;
import com.ttareureung.api.domain.profile.entity.Profile;
import com.ttareureung.api.domain.profile.entity.ProfileRole;
import com.ttareureung.api.domain.profile.repository.ProfileRepository;
import com.ttareureung.api.domain.guardian.service.SeniorAccessValidator;
import com.ttareureung.api.global.exception.CustomException;
import com.ttareureung.api.domain.profile.service.ProfileAccessChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MedicationService {

    private static final long MAX_PERIOD_DAYS = 366;  // 1년으로 가정
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MedicationLogRepository medicationLogRepository;
    private final MedicationRepository medicationRepository;
    private final ProfileRepository profileRepository;
    private final SeniorAccessValidator seniorAccessValidator;
    private final ProfileAccessChecker accessChecker;
    private final FileUrlResolver fileUrlResolver;  // image_url 키를 조회 링크로 변환

    @Transactional(readOnly = true)
    public DailyScheduleResponse getDailySchedule(Long accountId, Long seniorId, LocalDate date) {
        seniorAccessValidator.requireSenior(accountId, seniorId);
        List<MedicationLog> logs = medicationLogRepository.findDailyLogs(seniorId, date);

        Map<MealTimeSlot, List<MedicationLog>> grouped = logs.stream()
                .collect(Collectors.groupingBy(MedicationLog::getMealTimeSlot, TreeMap::new, Collectors.toList()));

        List<TimeSlot> timeSlots = grouped.entrySet().stream()
                .map(e -> toTimeSlot(e.getKey(), e.getValue()))
                .toList();

        return new DailyScheduleResponse(date, timeSlots);
    }

    @Transactional
    public UpdateLogStatusResponse updateLogStatus(Long accountId, Long logId, MedicationStatus status) {
        accessChecker.activeAccount(accountId);
        MedicationLog log = medicationLogRepository.findById(logId)
                .orElseThrow(() -> new CustomException(MedicationErrorCode.MEDICATION_LOG_NOT_FOUND));

        if (!seniorAccessValidator.hasAccess(accountId, log.getMedication().getSeniorProfileId())) {
            throw new CustomException(MedicationErrorCode.MEDICATION_LOG_NOT_FOUND);
        }

        log.changeStatus(status);
        return new UpdateLogStatusResponse(log.getId(), log.getStatus(), "복용 상태가 변경되었습니다.");
    }

    @Transactional
    public MedicationCreateResponse register(Long accountId, MedicationCreateRequest req) {
        accessChecker.activeAccount(accountId);
        seniorAccessValidator.requireSenior(accountId, req.seniorProfileId());
        Long id = createOne(req.seniorProfileId(), req.toItem());
        return new MedicationCreateResponse(id, "약 복용 일정이 등록되어 있습니다.");
    }

    @Transactional // 하나라도 실패하면 전체 롤백
    public MedicationBulkCreateResponse registerBulk(Long accountId, MedicationBulkCreateRequest req) {
        accessChecker.activeAccount(accountId);
        seniorAccessValidator.requireSenior(accountId, req.seniorProfileId());
        List<Long> ids = req.medications().stream()
                .map(item -> createOne(req.seniorProfileId(), item))
                .toList();
        return new MedicationBulkCreateResponse(ids, ids.size() + "개의 약 복용 일정이 등록되었습니다.");
    }

    @Transactional
    public MedicationBulkDeleteResponse deleteMedications(Long accountId, List<Long> medicationIds) {
        accessChecker.activeAccount(accountId);
        List<Long> ids = medicationIds.stream().distinct().toList();

        List<Medication> found = medicationRepository.findAllById(ids);
        if (found.size() != ids.size()
                || !found.stream().map(Medication::getSeniorProfileId).distinct()
                .allMatch(sid -> seniorAccessValidator.hasAccess(accountId, sid))) {
            throw new CustomException(MedicationErrorCode.MEDICATION_NOT_FOUND);
        }

        medicationLogRepository.deleteByMedicationIds(ids);
        medicationRepository.deleteAllByIdInBatch(ids);
        return new MedicationBulkDeleteResponse(ids.size(), ids.size() + "개의 약 복용 일정이 삭제되었습니다.");
    }

    @Transactional(readOnly = true)
    public MedicationListResponse getMedications(Long accountId, Long seniorId) {
        seniorAccessValidator.requireSenior(accountId, seniorId);
        List<Medication> medications = medicationRepository.findBySeniorProfileIdOrderByCreatedAtDesc(seniorId);

        Map<Long, List<MealTimeSlot>> slots = new HashMap<>();
        if (!medications.isEmpty()) {
            for (Object[] row : medicationLogRepository.findSlotsByMedicationIds(
                    medications.stream().map(Medication::getId).toList())) {
                slots.computeIfAbsent((Long) row[0], k -> new ArrayList<>()).add((MealTimeSlot) row[1]);
            }
            slots.values().forEach(Collections::sort); // 아침 점심 저녁 자기 전
        }

        List<MedicationListResponse.MedicationItem> items = medications.stream()
                .map(m -> new MedicationListResponse.MedicationItem(
                        m.getId(),
                        m.getDrugName(),
                        m.getCustomDrugName(),
                        m.getCategory(),
                        fileUrlResolver.resolve(m.getImageUrl()),
                        m.getPrescriptionFileName(),
                        m.getRegistrationType(),
                        m.getEffect(),
                        m.getStorageLocation(),
                        m.getDoseAmount(),
                        m.getAppearance(),
                        m.getStrength(),
                        m.getIntakeReason(),
                        m.getFrequency(),
                        m.getCycleType() != null ? CycleType.valueOf(m.getCycleType()) : null,
                        m.getIntervalDays(),
                        m.getIntakeTiming(),
                        slots.getOrDefault(m.getId(), new ArrayList<>()),
                        m.isNotifyOnMiss(),
                        m.getStartDate(),
                        m.getEndDate(),
                        m.getCreatedAt()
                ))
                .toList();

        return new MedicationListResponse(items);
    }

    @Transactional
    public MedicationUpdateResponse updateMedication(Long accountId, Long medicationId, MedicationUpdateRequest req) {
        accessChecker.activeAccount(accountId);
        Medication m = findOwnedMedication(accountId, medicationId);

        // 일정 관련 새 값
        LocalDate newStart = req.startDate() != null ? req.startDate() : m.getStartDate();
        LocalDate newEnd = req.endDate() != null ? req.endDate() : m.getEndDate();
        int newInterval = req.intervalDays() != null ? req.intervalDays() : m.getIntervalDays();
        CycleType newCycle = req.cycleType() != null ? req.cycleType()
                : (m.getCycleType() != null ? CycleType.valueOf(m.getCycleType()) : CycleType.DAILY);
        if (req.startDate() != null || req.endDate() != null) {
            validatePeriod(newStart, newEnd);
        }

        Set<MealTimeSlot> currentSlots = currentSlots(m.getId());
        Set<MealTimeSlot> newSlots = req.mealTimeSlots() != null
                ? EnumSet.copyOf(req.mealTimeSlots())
                : currentSlots;

        boolean intervalChanged = newInterval != m.getIntervalDays();
        boolean scheduleChanged = intervalChanged
                || !newStart.equals(m.getStartDate())
                || !newEnd.equals(m.getEndDate())
                || !newSlots.equals(currentSlots);
        boolean timingChanged = req.intakeTiming() != null && req.intakeTiming() != m.getIntakeTiming();

        m.update(
                req.customDrugName(),
                req.frequency(),
                req.effect(),
                req.imageUrl(),
                req.notifyOnMiss() != null ? req.notifyOnMiss() : m.isNotifyOnMiss(),
                req.storageLocation(),
                req.doseAmount(),
                req.appearance(),
                req.strength(),
                req.intakeReason()
        );
        m.updateInfo(req.drugName(), req.category(), req.registrationType(), req.intakeTiming());

        if (scheduleChanged) {
            m.changeSchedule(newStart, newEnd, newCycle, newInterval);
            syncLogs(m, newSlots);
        }
        // frequency를 직접 보내지 않았는데 간격·시점이 바뀌면 표시 문장도 다시 만든다
        if (req.frequency() == null && (intervalChanged || timingChanged)) {
            m.changeFrequency(FrequencyFormatter.compose(m.getIntervalDays(), m.getIntakeTiming()));
        }
        return new MedicationUpdateResponse(m.getId(), "약 정보가 수정되었습니다.");
    }

    @Transactional
    public void deleteMedication(Long accountId, Long medicationId) {
        accessChecker.activeAccount(accountId);
        Medication m = findOwnedMedication(accountId, medicationId);
        medicationLogRepository.deleteByMedicationIds(List.of(medicationId));
        medicationRepository.delete(m);
    }

    private Medication findOwnedMedication(Long accountId, Long medicationId) {
        Medication m = medicationRepository.findById(medicationId)
                .orElseThrow(() -> new CustomException(MedicationErrorCode.MEDICATION_NOT_FOUND));
        if (!seniorAccessValidator.hasAccess(accountId, m.getSeniorProfileId())) {
            throw new CustomException(MedicationErrorCode.MEDICATION_NOT_FOUND);
        }
        return m;
    }

    private void validatePeriod(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw new CustomException(MedicationErrorCode.INVALID_DATE_RANGE);
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_PERIOD_DAYS) {
            throw new CustomException(MedicationErrorCode.PERIOD_TOO_LONG);
        }
    }

    private Long createOne(Long seniorProfileId, MedicationItemRequest item) {
        validatePeriod(item.startDate(), item.endDate());

        int interval = item.intervalDays() != null ? item.intervalDays() : 1;
        String frequency = (item.frequency() != null && !item.frequency().isBlank())
                ? item.frequency()
                : FrequencyFormatter.compose(interval, item.intakeTiming());

        Medication medication = medicationRepository.save(Medication.builder()
                .seniorProfileId(seniorProfileId)
                .drugName(item.drugName())
                .customDrugName(item.customDrugName())
                .category(item.category())
                .imageUrl(item.imageUrl())
                .prescriptionFileName(item.prescriptionFileName())
                .registrationType(item.registrationType() != null ? item.registrationType() : RegistrationType.MANUAL)
                .effect(item.effect())
                .storageLocation(item.storageLocation())
                .doseAmount(item.doseAmount())
                .appearance(item.appearance())
                .strength(item.strength())
                .intakeReason(item.intakeReason())
                .startDate(item.startDate())
                .endDate(item.endDate())
                .cycleType(item.cycleType() != null ? item.cycleType().name() : null)
                .intervalDays(interval)
                .intakeTiming(item.intakeTiming())
                .frequency(frequency)
                .notifyOnMiss(item.notifyOnMiss())
                .build());

        List<MealTimeSlot> slots = item.mealTimeSlots().stream().distinct().toList();
        List<MedicationLog> logs = new ArrayList<>();
        for (LocalDate d = item.startDate(); !d.isAfter(item.endDate()); d = d.plusDays(interval)) {
            for (MealTimeSlot slot : slots) {
                logs.add(MedicationLog.create(medication, d, slot));
            }
        }
        medicationLogRepository.saveAll(logs);
        return medication.getId();
    }

    private Set<MealTimeSlot> currentSlots(Long medicationId) {
        Set<MealTimeSlot> slots = EnumSet.noneOf(MealTimeSlot.class);
        for (Object[] row : medicationLogRepository.findSlotsByMedicationIds(List.of(medicationId))) {
            slots.add((MealTimeSlot) row[1]);
        }
        return slots;
    }

    /**
     * 일정이 바뀐 약의 복용 기록 동기화.
     * - 오늘 이전 기록은 건드리지 않는다 (복용 이력 보존)
     * - 오늘 이후 PENDING 중 새 일정에 맞지 않는 것은 삭제, 복용·미복용으로 확정된 기록은 보존
     * - 오늘 이후 새 일정에 필요한데 없는 기록은 PENDING으로 생성
     */
    private void syncLogs(Medication m, Set<MealTimeSlot> slots) {
        LocalDate today = LocalDate.now(KST);
        List<MedicationLog> fromToday =
                medicationLogRepository.findByMedicationIdAndTargetDateGreaterThanEqual(m.getId(), today);

        Set<Long> staleIds = fromToday.stream()
                .filter(l -> l.getStatus() == MedicationStatus.PENDING)
                .filter(l -> !m.isScheduledOn(l.getTargetDate()) || !slots.contains(l.getMealTimeSlot()))
                .map(MedicationLog::getId)
                .collect(Collectors.toSet());
        if (!staleIds.isEmpty()) {
            medicationLogRepository.deleteAllByIdInBatch(staleIds);
        }

        Set<String> existing = fromToday.stream()
                .filter(l -> !staleIds.contains(l.getId()))
                .map(l -> l.getTargetDate() + "|" + l.getMealTimeSlot())
                .collect(Collectors.toSet());

        // 오늘 이후의 첫 복용일부터 간격대로 생성
        LocalDate from = m.getStartDate().isAfter(today) ? m.getStartDate() : today;
        long offset = ChronoUnit.DAYS.between(m.getStartDate(), from);
        LocalDate first = m.getStartDate().plusDays(Math.ceilDiv(offset, (long) m.getIntervalDays()) * m.getIntervalDays());

        List<MedicationLog> created = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(m.getEndDate()); d = d.plusDays(m.getIntervalDays())) {
            for (MealTimeSlot slot : slots) {
                if (!existing.contains(d + "|" + slot)) {
                    created.add(MedicationLog.create(m, d, slot));
                }
            }
        }
        medicationLogRepository.saveAll(created);
    }

    private TimeSlot toTimeSlot(MealTimeSlot slot, List<MedicationLog> logs) {
        List<Medicine> medicines = logs.stream()
                .map(l -> new Medicine(l.getId(), l.getMedication().getId(),
                        l.getMedication().getDisplayName(), l.getMedication().getDoseAmount(),
                        l.getMedication().getIntakeTiming(), l.getStatus()))
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