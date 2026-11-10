package com.ttareureung.api.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TokenResponse(
        @Schema(description = "JWT Access Token") String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "Access Token 유효 시간(초)", example = "1800") long expiresIn
) {
}
