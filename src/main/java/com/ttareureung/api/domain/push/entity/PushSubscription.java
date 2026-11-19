package com.ttareureung.api.domain.push.entity;

import com.ttareureung.api.domain.profile.entity.Profile;
import com.ttareureung.api.global.common.BaseTimeEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * 프로필별 브라우저 푸시 구독. 같은 기기(endpoint)는 한 행만 유지
 * 가족 공용 기기에서 프로필을 바꾸면 마지막으로 등록한 프로필로 이동
 */
@Getter
@Entity
@Table(name = "push_subscriptions", uniqueConstraints = @UniqueConstraint(
        name = "uk_push_subscriptions_endpoint", columnNames = "endpoint"))
@AttributeOverrides({
        @AttributeOverride(name = "createdAt", column = @Column(name = "created_at", nullable = false, updatable = false)),
        @AttributeOverride(name = "updatedAt", column = @Column(name = "updated_at", nullable = false))
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushSubscription extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    // 브라우저 푸시 서비스 주소. 길이가 길어 500자
    @Column(nullable = false, length = 500)
    private String endpoint;

    @Column(nullable = false, length = 200)
    private String p256dh;

    @Column(nullable = false, length = 100)
    private String auth;

    public PushSubscription(Profile profile, String endpoint, String p256dh, String auth) {
        this.profile = profile;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    public void renew(Profile profile, String p256dh, String auth) {
        this.profile = profile;
        this.p256dh = p256dh;
        this.auth = auth;
    }
}
