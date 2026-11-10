package com.ttareureung.api.domain.medication.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "medications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Medication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "senior_profile_id", nullable = false)
    private Long seniorProfileId; // 이후 Profile 연관관계로 수정 예정

    @Column(name = "drug_name", nullable = false, length = 100)
    private String drugName;

    @Column(name = "custom_drug_name", length = 100)
    private String customDrugName;

    public String getDisplayName() {
        return (customDrugName != null && !customDrugName.isBlank()) ? customDrugName : drugName;
}
