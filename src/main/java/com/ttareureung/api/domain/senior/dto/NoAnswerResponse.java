package com.ttareureung.api.domain.senior.dto;

import com.ttareureung.api.domain.senior.entity.DoseSessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record NoAnswerResponse(
        @Schema(example = "15") Long sessionId,
        @Schema(description = "3회째면 MISSED") DoseSessionStatus status,
        @Schema(example = "1") int callAttempts,
        @Schema(description = "재전화 시각. MISSED면 null", example = "2026-11-08T15:10:00") LocalDateTime nextCallAt
) {
}
