package com.ttareureung.api.domain.medication.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "medication")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Medication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "senior_profile_id", nullable = false)
    private Long seniorProfileId;

    @Column(name = "drug_name", nullable = false)
    private String drugName;

    @Column(name = "custom_drug_name")
    private String customDrugName;

    @Column(length = 50)
    private String category;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "prescription_file_name")
    private String prescriptionFileName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "registration_type", length = 20)
    private RegistrationType registrationType;

    private String effect;

    @Column(name = "storage_location")
    private String storageLocation;

    // 시니어 복용 화면의 "2정", "1포". 숫자가 아닌 단위 표현이라 문자열
    @Column(name = "dose_amount", length = 20)
    private String doseAmount;

    // 시니어 약 찾기 화면의 "파란색 원기둥". 약 판독 프롬프트 힌트로도 사용
    @Column(length = 50)
    private String appearance;

    // 함량("5mg", "100mg"). 약 카드 이름 아래 표시. 1회 복용 수량(dose_amount)과 별개
    @Column(length = 30)
    private String strength;

    @Column(name = "intake_reason")
    private String intakeReason;

    // 화면 표시용 문장. 간격·시점은 아래 구조화 필드가 기준
    private String frequency;

    @Column(name = "cycle_type")
    private String cycleType;

    // 복용 간격(일). 1=매일, 2=이틀에 한 번, 7=일주일에 한 번. 기존 데이터는 기본값 1
    @Column(name = "interval_days", nullable = false, columnDefinition = "int not null default 1")
    private int intervalDays = 1;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "intake_timing", length = 30)
    private IntakeTiming intakeTiming;

    @Column(name = "notify_on_miss")
    private boolean notifyOnMiss;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public Medication(Long seniorProfileId, String drugName, String customDrugName, String category,
                      String imageUrl, String prescriptionFileName, RegistrationType registrationType,
                      String effect, String storageLocation, String doseAmount, String appearance,
                      String strength, String intakeReason, String frequency, String cycleType, Integer intervalDays,
                      IntakeTiming intakeTiming, boolean notifyOnMiss, LocalDate startDate, LocalDate endDate) {
        this.seniorProfileId = seniorProfileId;
        this.drugName = drugName;
        this.customDrugName = customDrugName;
        this.category = category;
        this.imageUrl = imageUrl;
        this.prescriptionFileName = prescriptionFileName;
        this.registrationType = registrationType;
        this.effect = effect;
        this.storageLocation = storageLocation;
        this.doseAmount = doseAmount;
        this.appearance = appearance;
        this.strength = strength;
        this.intakeReason = intakeReason;
        this.frequency = frequency;
        this.cycleType = cycleType;
        this.intervalDays = intervalDays == null ? 1 : intervalDays;
        this.intakeTiming = intakeTiming;
        this.notifyOnMiss = notifyOnMiss;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void update(String customDrugName, String frequency, String effect, String imageUrl,
                       boolean notifyOnMiss, String storageLocation, String doseAmount, String appearance,
                       String strength, String intakeReason) {

        // 빈 문자열은 비우기, null은 유지
        if (doseAmount != null) {
            this.doseAmount = doseAmount.isEmpty() ? null : doseAmount;
        }
        if (appearance != null) {
            this.appearance = appearance.isEmpty() ? null : appearance;
        }
        if (strength != null) {
            this.strength = strength.isEmpty() ? null : strength;
        }
        if (customDrugName != null) {
            this.customDrugName = customDrugName;
        }
        if (frequency != null) {
            this.frequency = frequency;
        }
        if (effect != null) {
            this.effect = effect;
        }
        if (imageUrl != null) {
            this.imageUrl = imageUrl;
        }
        this.notifyOnMiss = notifyOnMiss;
        if (storageLocation != null) {
            this.storageLocation = storageLocation;
        }
        if (intakeReason != null) {
            this.intakeReason = intakeReason.isEmpty() ? null : intakeReason;
        }
    }

    // 약 이름·종류·등록 방식·복용 시점. null은 유지, 종류는 빈 문자열이면 삭제
    public void updateInfo(String drugName, String category, RegistrationType registrationType,
                           IntakeTiming intakeTiming) {
        if (drugName != null) {
            this.drugName = drugName;
        }
        if (category != null) {
            this.category = category.isEmpty() ? null : category;
        }
        if (registrationType != null) {
            this.registrationType = registrationType;
        }
        if (intakeTiming != null) {
            this.intakeTiming = intakeTiming;
        }
    }

    // 기록 동기화는 호출자가 처리
    public void changeSchedule(LocalDate startDate, LocalDate endDate, CycleType cycleType, int intervalDays) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.cycleType = cycleType.name();
        this.intervalDays = intervalDays;
    }

    public void changeFrequency(String frequency) {
        this.frequency = frequency;
    }

    // 시작일부터 intervalDays마다가 복용일
    public boolean isScheduledOn(LocalDate date) {
        if (date.isBefore(startDate) || date.isAfter(endDate)) {
            return false;
        }
        return ChronoUnit.DAYS.between(startDate, date) % intervalDays == 0;
    }

    // 시니어가 못 먹은 약은 복용일 하나를 뒤에 추가. 늘어난 날짜의 복용 기록은 호출자가 생성
    public LocalDate extendToNextOccurrence() {
        long occurrences = ChronoUnit.DAYS.between(startDate, endDate) / intervalDays;
        this.endDate = startDate.plusDays((occurrences + 1) * intervalDays);
        return this.endDate;
    }

    // 기존 호출 호환용. 간격이 1일이 아닌 약에는 extendToNextOccurrence 사용
    public void extendEndDate(int days) {
        this.endDate = this.endDate.plusDays(days);
    }

    public RegistrationType getRegistrationType() {
        return registrationType == null ? RegistrationType.MANUAL : registrationType;
    }

    public String getDisplayName() {
        return customDrugName != null && !customDrugName.isBlank() ? customDrugName : drugName;
    }
}