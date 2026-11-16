package com.ttareureung.api.domain.senior.service;

import com.ttareureung.api.domain.medication.entity.MedicationLog;
import com.ttareureung.api.domain.medication.entity.MedicationStatus;
import com.ttareureung.api.domain.senior.entity.DoseSession;
import com.ttareureung.api.domain.senior.entity.GuardianAlertType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 회차가 미복용으로 끝났을 때의 공통 처리.
 * 전화 미응답 3회(DoseCallService)와 결과 화면 미복용 3회(DoseFinishService)가 같은 규칙
 */
@Component
public class DoseSessionOutcome {

    private final MedicationExtensionService extension;
    private final GuardianAlertService alertService;

    public DoseSessionOutcome(MedicationExtensionService extension, GuardianAlertService alertService) {
        this.extension = extension;
        this.alertService = alertService;
    }

    // 아직 안 먹은 약 전부 MISSED → 하루 연장 → 보호자 알림. 반환값은 연장된 약 수
    @Transactional(propagation = Propagation.MANDATORY)
    public int missRemaining(DoseSession session, List<MedicationLog> slotLogs) {
        List<MedicationLog> pending = slotLogs.stream()
                .filter(l -> l.getStatus() == MedicationStatus.PENDING)
                .toList();
        pending.forEach(l -> l.changeStatus(MedicationStatus.MISSED));
        int extended = extension.extendOneDay(pending);
        alertService.notify(session, GuardianAlertType.MISSED);
        return extended;
    }
}
