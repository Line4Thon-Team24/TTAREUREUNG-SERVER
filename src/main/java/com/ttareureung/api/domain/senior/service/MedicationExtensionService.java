package com.ttareureung.api.domain.senior.service;

import com.ttareureung.api.domain.medication.entity.MealTimeSlot;
import com.ttareureung.api.domain.medication.entity.Medication;
import com.ttareureung.api.domain.medication.entity.MedicationLog;
import com.ttareureung.api.domain.medication.repository.MedicationLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 못 먹은 약의 일정 하루 연장.
 * 복용 기록이 날짜별로 미리 만들어져 있어 end_date만 늘리면 안 되고 늘어난 날짜의 기록도 생성
 */
@Service
public class MedicationExtensionService {

    private final MedicationLogRepository logs;

    public MedicationExtensionService(MedicationLogRepository logs) {
        this.logs = logs;
    }

    // 회차 종료 트랜잭션 안에서만 호출. 반환값은 연장된 약 수
    @Transactional(propagation = Propagation.MANDATORY)
    public int extendOneDay(List<MedicationLog> missedLogs) {
        Map<Long, Medication> medications = new LinkedHashMap<>();
        missedLogs.forEach(l -> medications.putIfAbsent(l.getMedication().getId(), l.getMedication()));
        if (medications.isEmpty()) {
            return 0;
        }
        Map<Long, List<MealTimeSlot>> slots = new LinkedHashMap<>();
        for (Object[] row : logs.findSlotsByMedicationIds(medications.keySet())) {
            slots.computeIfAbsent((Long) row[0], k -> new java.util.ArrayList<>()).add((MealTimeSlot) row[1]);
        }
        for (Medication m : medications.values()) {
            m.extendEndDate(1);
            LocalDate added = m.getEndDate();
            for (MealTimeSlot slot : slots.getOrDefault(m.getId(), List.of())) {
                if (!logs.existsByMedicationIdAndTargetDateAndMealTimeSlot(m.getId(), added, slot)) {
                    logs.save(MedicationLog.create(m, added, slot));
                }
            }
        }
        return medications.size();
    }
}
