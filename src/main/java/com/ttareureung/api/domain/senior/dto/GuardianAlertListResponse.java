package com.ttareureung.api.domain.senior.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record GuardianAlertListResponse(
        @Schema(description = "최신순") List<GuardianAlertResponse> alerts,
        @Schema(description = "알림 설정의 N분 안에 울릴 회차") List<UpcomingCall> upcoming
) {
}
