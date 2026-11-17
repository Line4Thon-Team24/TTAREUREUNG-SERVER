package com.ttareureung.api.domain.senior.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record DoseTimesResponse(
        @Schema(example = "2") Long seniorId,
        List<DoseTimeItem> doseTimes
) {
}
