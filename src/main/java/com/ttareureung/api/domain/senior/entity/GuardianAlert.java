package com.ttareureung.api.domain.senior.entity;

import com.ttareureung.api.domain.profile.entity.Profile;
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
 * 보호자에게 보여 줄 알림. 푸시 없이 보호자 화면이 조회해서 표시
 * 읽음은 알림 단위 1개(보호자 여러 명이어도 한 명이 읽으면 읽음)
 */
@Getter
@Entity
@Table(name = "guardian_alerts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuardianAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "senior_profile_id", nullable = false)
    private Profile senior;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dose_session_id", nullable = false)
    private DoseSession session;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private GuardianAlertType type;

    // null이면 안 읽음
    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public GuardianAlert(DoseSession session, GuardianAlertType type, LocalDateTime createdAt) {
        this.senior = session.getSenior();
        this.session = session;
        this.type = type;
        this.createdAt = createdAt;
    }

    // 이미 읽은 알림은 처음 읽은 시각 유지
    public void markRead(LocalDateTime now) {
        if (readAt == null) {
            readAt = now;
        }
    }
}
