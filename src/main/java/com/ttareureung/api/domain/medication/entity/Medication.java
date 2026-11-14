package com.ttareureung.api.domain.medication.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

    @Column(name = "image_url")
    private String imageUrl;

    private String effect;

    @Column(name = "storage_location")
    private String storageLocation;

    @Column(name = "intake_reason")
    private String intakeReason;

    private String frequency;

    @Column(name = "cycle_type")
    private String cycleType;

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
    public Medication(Long seniorProfileId, String drugName, String customDrugName, String imageUrl,
                      String effect, String storageLocation, String intakeReason, String frequency,
                      String cycleType, boolean notifyOnMiss, LocalDate startDate, LocalDate endDate) {
        this.seniorProfileId = seniorProfileId;
        this.drugName = drugName;
        this.customDrugName = customDrugName;
        this.imageUrl = imageUrl;
        this.effect = effect;
        this.storageLocation = storageLocation;
        this.intakeReason = intakeReason;
        this.frequency = frequency;
        this.cycleType = cycleType;
        this.notifyOnMiss = notifyOnMiss;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void update(String customDrugName, String frequency, String effect, String imageUrl,
                       boolean notifyOnMiss, String storageLocation, String intakeReason) {

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

    public String getDisplayName() {
        return customDrugName != null && !customDrugName.isBlank() ? customDrugName : drugName;
    }
}