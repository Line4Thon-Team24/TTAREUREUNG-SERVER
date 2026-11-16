package com.ttareureung.api.domain.notification.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification_setting",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_setting_profile", columnNames = "profile_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Column(name = "notify_on_complete", nullable = false)
    private boolean notifyOnComplete = true;

    @Column(name = "notify_on_miss", nullable = false)
    private boolean notifyOnMiss = true;

    @Column(name = "notify_on_delay", nullable = false)
    private boolean notifyOnDelay = true;

    @Column(name = "remind_before_minutes", nullable = false)
    private int remindBeforeMinutes = 10;

    public static NotificationSetting defaultsFor(Long profileId) {
        NotificationSetting setting = new NotificationSetting();
        setting.profileId = profileId;
        return setting;
    }

    /** null인 항목은 유지 */
    public void update(Boolean notifyOnComplete, Boolean notifyOnMiss, Boolean notifyOnDelay, Integer remindBeforeMinutes) {
        if (notifyOnComplete != null) this.notifyOnComplete = notifyOnComplete;
        if (notifyOnMiss != null) this.notifyOnMiss = notifyOnMiss;
        if (notifyOnDelay != null) this.notifyOnDelay = notifyOnDelay;
        if (remindBeforeMinutes != null) this.remindBeforeMinutes = remindBeforeMinutes;
    }
}
