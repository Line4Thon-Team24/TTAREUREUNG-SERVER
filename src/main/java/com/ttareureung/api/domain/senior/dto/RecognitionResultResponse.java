package com.ttareureung.api.domain.senior.dto;

import com.ttareureung.api.domain.senior.entity.RecognitionResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record RecognitionResultResponse(
        @Schema(example = "31") Long recognitionId,
        @Schema(example = "7") Long medicationId,
        @Schema(description = "MATCH → 복용 화면, MISMATCH → 다시 찾기, UNCERTAIN → 사진 보고 직접 선택") RecognitionResult result,
        @Schema(description = "화면 문구", example = "맞는 약이에요!") String message,
        @Schema(description = "TTS로 읽을 문장") String voiceText,
        @Schema(description = "이 회차·약에 남은 판독 횟수", example = "9") int attemptsLeft
) {
}
