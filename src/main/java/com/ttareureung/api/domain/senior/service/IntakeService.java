package com.ttareureung.api.domain.senior.service;

import com.ttareureung.api.domain.medication.entity.IntakeVerification;
import com.ttareureung.api.domain.medication.entity.MedicationLog;
import com.ttareureung.api.domain.medication.entity.MedicationStatus;
import com.ttareureung.api.domain.profile.service.ProfileAccessChecker;
import com.ttareureung.api.domain.senior.dto.IntakeResponse;
import com.ttareureung.api.domain.senior.dto.MedicationGuide;
import com.ttareureung.api.domain.senior.entity.DoseSession;
import com.ttareureung.api.domain.senior.entity.RecognitionResult;
import com.ttareureung.api.domain.senior.exception.SeniorErrorCode;
import com.ttareureung.api.global.exception.CustomException;
import com.ttareureung.api.domain.senior.repository.MedicationRecognitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 시니어 "다 먹었어" 처리.
 * 판독 MATCH만으로는 복용으로 기록하지 않고, 이 호출이 있어야 TAKEN
 */
@Service
@Transactional
public class IntakeService {

    private final ProfileAccessChecker accessChecker;
    private final DoseSessionFinder finder;
    private final MedicationRecognitionRepository recognitions;
    private final Clock clock;

    public IntakeService(ProfileAccessChecker accessChecker, DoseSessionFinder finder,
                         MedicationRecognitionRepository recognitions, Clock clock) {
        this.accessChecker = accessChecker;
        this.finder = finder;
        this.recognitions = recognitions;
        this.clock = clock;
    }

    public IntakeResponse intake(Long accountId, Long sessionId, Long medicationId) {
        accessChecker.activeAccount(accountId);
        DoseSession session = finder.lockedAccessible(accountId, sessionId);
        finder.requireInProgress(session);
        List<MedicationLog> slotLogs = finder.logsOf(session);
        MedicationLog target = slotLogs.stream()
                .filter(l -> l.getMedication().getId().equals(medicationId))
                .findFirst()
                .orElseThrow(() -> new CustomException(
                        SeniorErrorCode.MEDICATION_NOT_IN_SESSION));

        // 같은 약 중복 요청은 처음 기록 유지
        if (target.getStatus() != MedicationStatus.TAKEN) {
            target.markTaken(LocalDateTime.now(clock.withZone(SeniorTime.ZONE)), verificationOf(sessionId, medicationId));
        }

        List<MedicationLog> remaining = slotLogs.stream()
                .filter(l -> l.getStatus() != MedicationStatus.TAKEN)
                .toList();
        MedicationGuide next = remaining.isEmpty() ? null : MedicationGuide.of(1, remaining.get(0).getMedication());
        return new IntakeResponse(session.getId(), medicationId, target.getStatus(), target.getTakenAt(),
                target.getVerification(), remaining.size(), slotLogs.size(), next, VoiceText.afterIntake(remaining.size()));
    }

    // 직전 판독이 MATCH면 VISION. 판독 없이 또는 실패 후 직접 고른 경우 MANUAL
    private IntakeVerification verificationOf(Long sessionId, Long medicationId) {
        return recognitions.findTopBySessionIdAndMedicationIdOrderByIdDesc(sessionId, medicationId)
                .filter(r -> r.getResult() == RecognitionResult.MATCH)
                .map(r -> IntakeVerification.VISION)
                .orElse(IntakeVerification.MANUAL);
    }
}
