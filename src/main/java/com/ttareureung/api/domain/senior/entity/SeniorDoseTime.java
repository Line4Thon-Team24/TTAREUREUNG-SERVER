package com.ttareureung.api.domain.senior.entity;

import com.ttareureung.api.domain.medication.entity.MealTimeSlot;
import com.ttareureung.api.domain.profile.entity.Profile;
import com.ttareureung.api.global.common.BaseTimeEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

import java.time.LocalTime;
import java.util.Map;

/**
 * 시니어별 회차(아침·점심·저녁·밤) 전화 시각.
 * 같은 회차 약은 한 번에 전화하므로 약 단위가 아니라 시니어 단위로 저장
 */
@Getter
@Entity
@Table(name = "senior_dose_times", uniqueConstraints = @UniqueConstraint(
        name = "uk_senior_dose_times_slot", columnNames = {"senior_profile_id", "time_slot"}))
@AttributeOverrides({
        @AttributeOverride(name = "createdAt", column = @Column(name = "created_at", nullable = false, updatable = false)),
        @AttributeOverride(name = "updatedAt", column = @Column(name = "updated_at", nullable = false))
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeniorDoseTime extends BaseTimeEntity {

    // 행이 없는 시니어도 일정이 돌아가야 하므로 기본 시각 고정
    public static final Map<MealTimeSlot, LocalTime> DEFAULTS = Map.of(
            MealTimeSlot.MORNING, LocalTime.of(8, 0),
            MealTimeSlot.LUNCH, LocalTime.of(12, 30),
            MealTimeSlot.DINNER, LocalTime.of(18, 30),
            MealTimeSlot.BEFORE_SLEEP, LocalTime.of(22, 0)
    );

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "senior_profile_id", nullable = false)
    private Profile senior;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "time_slot", nullable = false, length = 20)
    private MealTimeSlot timeSlot;

    @Convert(converter = HourMinuteConverter.class)
    @Column(name = "dose_time", nullable = false, columnDefinition = "VARCHAR(5)")
    private LocalTime doseTime;

    public SeniorDoseTime(Profile senior, MealTimeSlot timeSlot, LocalTime doseTime) {
        this.senior = senior;
        this.timeSlot = timeSlot;
        this.doseTime = doseTime;
    }

    public void changeTime(LocalTime doseTime) {
        this.doseTime = doseTime;
    }
}
