package com.ttareureung.api.domain.senior.entity;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 약 판독 기록. 결과만 남기고 촬영 이미지는 저장하지 않음(개인정보·용량)
 * 복용 처리 시 VISION/MANUAL 구분과 회차·약별 판독 횟수 제한에 사용
 */
@Getter
@Entity
@Table(name = "medication_recognitions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MedicationRecognition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dose_session_id", nullable = false)
    private DoseSession session;

    // FK 제약 없음. 약이 삭제돼도 판독 기록은 유지
    @Column(name = "medication_id", nullable = false)
    private Long medicationId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private RecognitionResult result;

    // 모델이 답한 근거 또는 실패 사유
    @Column(length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public MedicationRecognition(DoseSession session, Long medicationId, RecognitionResult result,
                                 String reason, LocalDateTime createdAt) {
        this.session = session;
        this.medicationId = medicationId;
        this.result = result;
        this.reason = reason == null || reason.length() <= 255 ? reason : reason.substring(0, 255);
        this.createdAt = createdAt;
    }
}
