package com.ttareureung.api.domain.senior.entity;

import com.ttareureung.api.domain.medication.entity.MealTimeSlot;
import com.ttareureung.api.domain.profile.entity.Profile;
import com.ttareureung.api.global.common.BaseTimeEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 시니어의 하루 한 회차(예: 11/8 점심).
 * 배치 없이 오늘 일정 조회 시점에 생성, 전화·판독·복용 흐름이 이 행의 상태를 바꿈
 */
@Getter
@Entity
@Table(name = "dose_sessions", uniqueConstraints = @UniqueConstraint(
        name = "uk_dose_sessions_senior_date_slot", columnNames = {"senior_profile_id", "target_date", "time_slot"}))
@AttributeOverrides({
        @AttributeOverride(name = "createdAt", column = @Column(name = "created_at", nullable = false, updatable = false)),
        @AttributeOverride(name = "updatedAt", column = @Column(name = "updated_at", nullable = false))
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DoseSession extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "senior_profile_id", nullable = false)
    private Profile senior;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "time_slot", nullable = false, length = 20)
    private MealTimeSlot timeSlot;

    // KST 기준 전화 예정 시각
    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private DoseSessionStatus status;

    // 일정 화면의 "11:08 통화 확인"
    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    // 미응답 재전화 횟수 계산용
    @Column(name = "call_attempts", nullable = false)
    private int callAttempts;

    // 재전화 포함 다음 수신 시각. 수신화면 표시 여부 판단 기준
    @Column(name = "next_call_at")
    private LocalDateTime nextCallAt;

    public DoseSession(Profile senior, LocalDate targetDate, MealTimeSlot timeSlot, LocalDateTime scheduledAt) {
        this.senior = senior;
        this.targetDate = targetDate;
        this.timeSlot = timeSlot;
        this.scheduledAt = scheduledAt;
        this.status = DoseSessionStatus.SCHEDULED;
        this.callAttempts = 0;
        this.nextCallAt = scheduledAt;
    }

    // 회차 시각 변경 반영. 전화 전(SCHEDULED, 시도 0회)인 회차만 호출
    public boolean canReschedule() {
        return status == DoseSessionStatus.SCHEDULED && callAttempts == 0;
    }

    public void reschedule(LocalDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
        this.nextCallAt = scheduledAt;
    }
}
