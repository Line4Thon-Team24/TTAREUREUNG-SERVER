package com.ttareureung.api.domain.medication.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "medication_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MedicationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medication_id", nullable = false)
    private Medication medication;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_time_slot", nullable = false, length = 20)
    private MealTimeSlot mealTimeSlot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MedicationStatus status;

    @Column(name = "taken_at")
    private LocalDateTime takenAt;

    // 시니어 전화 흐름으로 복용한 경우만 기록. 보호자가 직접 바꾼 기록은 null
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private IntakeVerification verification;

    public MedicationLog(Medication medication, LocalDate targetDate, MealTimeSlot mealTimeSlot) {
        this.medication = medication;
        this.targetDate = targetDate;
        this.mealTimeSlot = mealTimeSlot;
        this.status = MedicationStatus.PENDING;
    }

    public static MedicationLog create(Medication medication, LocalDate targetDate, MealTimeSlot mealTimeSlot) {
        return new MedicationLog(medication, targetDate, mealTimeSlot);
    }

    public void changeStatus(MedicationStatus newStatus) {
        this.status = newStatus;
        this.takenAt = (newStatus == MedicationStatus.TAKEN) ? LocalDateTime.now() : null;
    }

    // 시니어 "다 먹었어" 처리. 시각은 KST 기준으로 호출자가 전달
    public void markTaken(LocalDateTime takenAt, IntakeVerification verification) {
        this.status = MedicationStatus.TAKEN;
        this.takenAt = takenAt;
        this.verification = verification;
    }
}
