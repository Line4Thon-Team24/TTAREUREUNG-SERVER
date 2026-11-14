package com.ttareureung.api.domain.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ttareureung.api.domain.notification.entity.NotificationSetting;

public record NotificationSettingResponse(
        @JsonProperty("profile_id") Long profileId,
        @JsonProperty("notify_on_complete") boolean notifyOnComplete,
        @JsonProperty("notify_on_miss") boolean notifyOnMiss,
        @JsonProperty("notify_on_delay") boolean notifyOnDelay,
        @JsonProperty("remind_before_minutes") int remindBeforeMinutes) {
    public static NotificationSettingResponse from(NotificationSetting s) {
        return new NotificationSettingResponse(s.getProfileId(), s.isNotifyOnComplete(),
                s.isNotifyOnMiss(), s.isNotifyOnDelay(), s.getRemindBeforeMinutes());
    }
}
