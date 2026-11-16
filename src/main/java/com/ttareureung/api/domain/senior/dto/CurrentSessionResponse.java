package com.ttareureung.api.domain.senior.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// 프론트가 1분 간격으로 조회. current가 있으면 수신화면 표시
public record CurrentSessionResponse(
        @Schema(description = "KST 서버 시각", example = "2026-11-08T15:01:00") LocalDateTime serverTime,
        @Schema(description = "지금 울려야 할 회차. 없으면 null") SessionCallResponse current,
        @Schema(description = "다음 회차. 오늘 남은 회차가 없으면 내일 첫 회차, 그것도 없으면 null") NextCallInfo next
) {
}
