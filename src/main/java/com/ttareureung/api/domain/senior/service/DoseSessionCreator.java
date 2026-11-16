package com.ttareureung.api.domain.senior.service;

import com.ttareureung.api.domain.medication.entity.MealTimeSlot;
import com.ttareureung.api.domain.profile.repository.ProfileRepository;
import com.ttareureung.api.domain.senior.entity.DoseSession;
import com.ttareureung.api.domain.senior.repository.DoseSessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 회차 행 생성 전용.
 * 일정 조회가 1분 간격 폴링이라 같은 회차를 동시에 만들 수 있음 → UNIQUE 위반을 별도 트랜잭션에서 받아
 * 조회 쪽 트랜잭션이 롤백되지 않게 분리
 */
@Component
public class DoseSessionCreator {

    private final DoseSessionRepository sessions;
    private final ProfileRepository profiles;

    public DoseSessionCreator(DoseSessionRepository sessions, ProfileRepository profiles) {
        this.sessions = sessions;
        this.profiles = profiles;
    }

    // UNIQUE 위반(DataIntegrityViolationException)은 호출자가 무시하고 다시 조회
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void create(Long seniorId, LocalDate date, MealTimeSlot slot, LocalDateTime scheduledAt) {
        sessions.saveAndFlush(new DoseSession(profiles.getReferenceById(seniorId), date, slot, scheduledAt));
    }
}
