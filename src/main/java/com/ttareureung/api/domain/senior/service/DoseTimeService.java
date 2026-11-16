package com.ttareureung.api.domain.senior.service;

import com.ttareureung.api.domain.guardian.service.SeniorAccessValidator;
import com.ttareureung.api.domain.medication.entity.MealTimeSlot;
import com.ttareureung.api.domain.profile.entity.Profile;
import com.ttareureung.api.domain.profile.service.ProfileAccessChecker;
import com.ttareureung.api.domain.senior.dto.DoseTimeItem;
import com.ttareureung.api.domain.senior.dto.DoseTimesResponse;
import com.ttareureung.api.domain.senior.dto.DoseTimesUpdateRequest;
import com.ttareureung.api.domain.senior.entity.DoseSession;
import com.ttareureung.api.domain.senior.entity.SeniorDoseTime;
import com.ttareureung.api.domain.senior.exception.SeniorErrorCode;
import com.ttareureung.api.domain.senior.repository.DoseSessionRepository;
import com.ttareureung.api.domain.senior.repository.SeniorDoseTimeRepository;
import com.ttareureung.api.global.exception.CustomException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 시니어별 회차 시각 조회·변경.
 * 저장된 행이 없으면 기본 시각으로 응답해 기존 시니어도 바로 일정 조회 가능
 */
@Service
@Transactional
public class DoseTimeService {

    private final ProfileAccessChecker accessChecker;
    private final SeniorAccessValidator seniorAccess;
    private final SeniorDoseTimeRepository doseTimes;
    private final DoseSessionRepository sessions;
    private final Clock clock;

    public DoseTimeService(ProfileAccessChecker accessChecker, SeniorAccessValidator seniorAccess,
                           SeniorDoseTimeRepository doseTimes, DoseSessionRepository sessions, Clock clock) {
        this.accessChecker = accessChecker;
        this.seniorAccess = seniorAccess;
        this.doseTimes = doseTimes;
        this.sessions = sessions;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DoseTimesResponse get(Long accountId, Long seniorId) {
        seniorAccess.requireSenior(accountId, seniorId);
        return toResponse(seniorId, effectiveTimes(seniorId));
    }

    public DoseTimesResponse update(Long accountId, Long seniorId, DoseTimesUpdateRequest request) {
        accessChecker.activeAccount(accountId);
        Profile senior = seniorAccess.requireSenior(accountId, seniorId);

        Map<MealTimeSlot, LocalTime> requested = new EnumMap<>(MealTimeSlot.class);
        for (DoseTimesUpdateRequest.Item item : request.doseTimes()) {
            if (requested.put(item.timeSlot(), item.doseTime()) != null) {
                throw new CustomException(SeniorErrorCode.DUPLICATE_TIME_SLOT);
            }
        }

        // 일부 회차만 보내도 전체 순서가 유지되는지 확인
        Map<MealTimeSlot, LocalTime> merged = effectiveTimes(seniorId);
        merged.putAll(requested);
        requireAscending(merged);

        Map<MealTimeSlot, SeniorDoseTime> stored = new EnumMap<>(MealTimeSlot.class);
        doseTimes.findAllBySeniorId(seniorId).forEach(t -> stored.put(t.getTimeSlot(), t));
        requested.forEach((slot, time) -> {
            SeniorDoseTime row = stored.get(slot);
            if (row == null) {
                doseTimes.save(new SeniorDoseTime(senior, slot, time));
            } else {
                row.changeTime(time);
            }
        });

        rescheduleToday(seniorId, requested);
        return toResponse(seniorId, merged);
    }

    // 회차 시각 조회 공용. 행이 없는 회차는 기본값
    public Map<MealTimeSlot, LocalTime> effectiveTimes(Long seniorId) {
        Map<MealTimeSlot, LocalTime> result = new EnumMap<>(SeniorDoseTime.DEFAULTS);
        doseTimes.findAllBySeniorId(seniorId).forEach(t -> result.put(t.getTimeSlot(), t.getDoseTime()));
        return result;
    }

    private static void requireAscending(Map<MealTimeSlot, LocalTime> times) {
        LocalTime previous = null;
        for (MealTimeSlot slot : MealTimeSlot.values()) {
            LocalTime current = times.get(slot);
            if (previous != null && !current.isAfter(previous)) {
                throw new CustomException(SeniorErrorCode.DOSE_TIME_ORDER_INVALID);
            }
            previous = current;
        }
    }

    // 이미 만들어진 오늘 회차 중 전화 전인 것만 새 시각으로. 진행 중·끝난 회차는 유지
    private void rescheduleToday(Long seniorId, Map<MealTimeSlot, LocalTime> changed) {
        LocalDate today = LocalDate.now(clock.withZone(SeniorTime.ZONE));
        for (DoseSession session : sessions.findAllBySeniorIdAndTargetDate(seniorId, today)) {
            LocalTime time = changed.get(session.getTimeSlot());
            if (time != null && session.canReschedule()) {
                session.reschedule(today.atTime(time));
            }
        }
    }

    private static DoseTimesResponse toResponse(Long seniorId, Map<MealTimeSlot, LocalTime> times) {
        List<DoseTimeItem> items = times.entrySet().stream()
                .map(e -> DoseTimeItem.of(e.getKey(), e.getValue()))
                .toList();
        return new DoseTimesResponse(seniorId, items);
    }
}
