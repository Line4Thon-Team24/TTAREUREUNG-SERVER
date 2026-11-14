package com.ttareureung.api.domain.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record NotificationSettingUpdateRequest(
        @JsonProperty("profile_id") @NotNull Long profileId,
        @JsonProperty("notify_on_complete") Boolean notifyOnComplete,
        @JsonProperty("notify_on_miss") Boolean notifyOnMiss,
        @JsonProperty("notify_on_delay") Boolean notifyOnDelay,
        @JsonProperty("remind_before_minutes") @Min(0) @Max(180) Integer remindBeforeMinutes) {}
