package com.ttareureung.api.domain.medication.service;

import com.ttareureung.api.domain.guardian.service.SeniorAccessValidator;
import com.ttareureung.api.domain.medication.dto.MonthlyCalendarResponse;
import com.ttareureung.api.domain.medication.dto.SeniorMedicationSummaryResponse;
import com.ttareureung.api.domain.medication.entity.MedicationLog;
import com.ttareureung.api.domain.medication.entity.MedicationStatus;
import com.ttareureung.api.domain.medication.repository.MedicationLogRepository;
import com.ttareureung.api.domain.medication.repository.MedicationRepository;
import com.ttareureung.api.domain.profile.exception.ProfileErrorCode;
import com.ttareureung.api.global.exception.CustomException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MedicationSummaryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final SeniorAccessValidator seniorAccessValidator;
    private final MedicationLogRepository medicationLogRepository;
    private final MedicationRepository medicationRepository;

    public MedicationSummaryService(SeniorAccessValidator seniorAccessValidator,
                                    MedicationLogRepository medicationLogRepository,
                                    MedicationRepository medicationRepository) {
        this.seniorAccessValidator = seniorAccessValidator;
        this.medicationLogRepository = medicationLogRepository;
        this.medicationRepository = medicationRepository;
    }

    @Transactional(readOnly = true)
    public MonthlyCalendarResponse getMonthly(Long accountId, Long seniorId, YearMonth month) {
        seniorAccessValidator.requireSenior(accountId, seniorId);

        Map<LocalDate, List<MedicationLog>> byDate = medicationLogRepository
                .findMonthlyLogs(seniorId, month.atDay(1), month.atEndOfMonth()).stream()
                .collect(Collectors.groupingBy(MedicationLog::getTargetDate, TreeMap::new, Collectors.toList()));

        int fullDays = 0, partialDays = 0, missedDays = 0;
        List<MonthlyCalendarResponse.Day> days = new ArrayList<>();
        for (Map.Entry<LocalDate, List<MedicationLog>> entry : byDate.entrySet()) {
            List<MedicationLog> logs = entry.getValue();
            int taken = (int) logs.stream().filter(l -> l.getStatus() == MedicationStatus.TAKEN).count();
            boolean anyMissed = logs.stream().anyMatch(l -> l.getStatus() == MedicationStatus.MISSED);
            String status = taken == logs.size() ? "TAKEN"
                    : taken > 0 ? "PARTIAL"
                      : anyMissed ? "MISSED" : "PENDING";
            switch (status) {
                case "TAKEN" -> fullDays++;
                case "PARTIAL" -> partialDays++;
                case "MISSED" -> missedDays++;
                default -> { }
            }
            days.add(new MonthlyCalendarResponse.Day(entry.getKey(), status, logs.size(), taken,
                    logs.stream().map(l -> l.getMedication().getDisplayName()).distinct().toList()));
        }

        int length = month.lengthOfMonth();
        return new MonthlyCalendarResponse(month.toString(), length, fullDays, partialDays, missedDays,
                percent(fullDays + partialDays, length),
                percent(fullDays, length), percent(partialDays, length), percent(missedDays, length), days);
    }

    @Transactional(readOnly = true)
    public SeniorMedicationSummaryResponse summarize(Long accountId, List<Long> seniorIds) {
        List<Long> ids = seniorIds.stream().distinct().toList();
        for (Long id : ids) {
            if (!seniorAccessValidator.hasAccess(accountId, id)) {
                throw new CustomException(ProfileErrorCode.PROFILE_NOT_FOUND);
            }
        }
        Map<Long, long[]> counts = new HashMap<>();
        for (Object[] row : medicationRepository.countBySenior(ids, LocalDate.now(KST))) {
            counts.put((Long) row[0], new long[]{(Long) row[1], (Long) row[2]});
        }
        return new SeniorMedicationSummaryResponse(ids.stream().map(id -> {
            long[] c = counts.getOrDefault(id, new long[]{0, 0});
            return new SeniorMedicationSummaryResponse.Item(id, c[0] > 0, c[1]);
        }).toList());
    }

    private static int percent(int part, int whole) {
        return (int) Math.round(part * 100.0 / whole);
    }
}
