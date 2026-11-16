package com.ttareureung.api.domain.senior.service;

import com.ttareureung.api.domain.guardian.service.SeniorAccessValidator;
import com.ttareureung.api.domain.medication.entity.MedicationLog;
import com.ttareureung.api.domain.medication.repository.MedicationLogRepository;
import com.ttareureung.api.domain.senior.entity.DoseSession;
import com.ttareureung.api.domain.senior.entity.DoseSessionStatus;
import com.ttareureung.api.domain.senior.exception.SeniorErrorCode;
import com.ttareureung.api.domain.senior.repository.DoseSessionRepository;
import com.ttareureung.api.global.exception.CustomException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 회차 조회·접근 확인 공통 로직.
 * 없는 회차와 권한 없는 회차 모두 404(SENIOR_003)로 통일
 */
@Component
public class DoseSessionFinder {

    private final DoseSessionRepository sessions;
    private final MedicationLogRepository logs;
    private final SeniorAccessValidator seniorAccess;

    public DoseSessionFinder(DoseSessionRepository sessions, MedicationLogRepository logs,
                             SeniorAccessValidator seniorAccess) {
        this.sessions = sessions;
        this.logs = logs;
        this.seniorAccess = seniorAccess;
    }

    // 상태를 바꾸는 작업용. 트랜잭션 종료까지 회차 행 잠금
    public DoseSession lockedAccessible(Long accountId, Long sessionId) {
        return requireAccessible(accountId, sessions.findByIdForUpdate(sessionId));
    }

    // 외부 호출이 끼는 작업용. 잠금 없음
    public DoseSession accessible(Long accountId, Long sessionId) {
        return requireAccessible(accountId, sessions.findByIdWithSenior(sessionId));
    }

    // 판독·복용은 전화를 받은 뒤에만
    public void requireInProgress(DoseSession session) {
        if (session.getStatus().isFinished()) {
            throw new CustomException(SeniorErrorCode.SESSION_FINISHED);
        }
        if (session.getStatus() != DoseSessionStatus.IN_PROGRESS) {
            throw new CustomException(SeniorErrorCode.SESSION_NOT_IN_PROGRESS);
        }
    }

    // 회차(시니어·날짜·회차)의 복용 기록. 약 등록 순
    public List<MedicationLog> logsOf(DoseSession session) {
        return logs.findDailyLogs(session.getSenior().getId(), session.getTargetDate()).stream()
                .filter(l -> l.getMealTimeSlot() == session.getTimeSlot())
                .toList();
    }

    public MedicationLog logOf(DoseSession session, Long medicationId) {
        return logsOf(session).stream()
                .filter(l -> l.getMedication().getId().equals(medicationId))
                .findFirst()
                .orElseThrow(() -> new CustomException(SeniorErrorCode.MEDICATION_NOT_IN_SESSION));
    }

    private DoseSession requireAccessible(Long accountId, Optional<DoseSession> found) {
        DoseSession session = found.orElseThrow(() -> new CustomException(SeniorErrorCode.SESSION_NOT_FOUND));
        if (!seniorAccess.hasAccess(accountId, session.getSenior().getId())) {
            throw new CustomException(SeniorErrorCode.SESSION_NOT_FOUND);
        }
        return session;
    }
}
