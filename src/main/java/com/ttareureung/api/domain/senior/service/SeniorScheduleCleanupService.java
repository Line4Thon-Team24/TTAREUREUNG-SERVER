package com.ttareureung.api.domain.senior.service;

import com.ttareureung.api.domain.senior.repository.DoseSessionRepository;
import com.ttareureung.api.domain.senior.repository.SeniorDoseTimeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 시니어 프로필 삭제 시 회차·복용 시각 정리. FK 때문에 프로필보다 먼저 삭제
@Service
public class SeniorScheduleCleanupService {

    private final DoseSessionRepository sessions;
    private final SeniorDoseTimeRepository doseTimes;

    public SeniorScheduleCleanupService(DoseSessionRepository sessions, SeniorDoseTimeRepository doseTimes) {
        this.sessions = sessions;
        this.doseTimes = doseTimes;
    }

    // 프로필 삭제 트랜잭션 안에서만 호출
    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteAllBySenior(Long seniorProfileId) {
        sessions.deleteAllBySeniorId(seniorProfileId);
        doseTimes.deleteAllBySeniorId(seniorProfileId);
    }
}
