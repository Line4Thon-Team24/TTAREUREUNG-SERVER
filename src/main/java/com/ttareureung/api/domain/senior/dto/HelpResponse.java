package com.ttareureung.api.domain.senior.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record HelpResponse(
        @Schema(example = "41") Long alertId,
        @Schema(example = "15") Long sessionId,
        @Schema(example = "2026-11-08T15:05:00") LocalDateTime createdAt,
        @Schema(description = "TTS로 읽을 문장") String voiceText
) {
}
