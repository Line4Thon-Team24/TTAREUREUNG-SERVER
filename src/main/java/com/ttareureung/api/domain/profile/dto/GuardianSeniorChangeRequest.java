package com.ttareureung.api.domain.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record GuardianSeniorChangeRequest(
        @NotNull @Schema(description = "새 담당 시니어 프로필 ID", example = "6") Long seniorProfileId
) {
}
