package com.ttareureung.api.domain.senior.dto;

import io.swagger.v3.oas.annotations.media.Schema;

// 시니어 "이 약을 찾아주세요" 카드 한 장
public record MedicationGuide(
        @Schema(description = "회차 안 순서. 1부터", example = "1") int order,
        @Schema(example = "7") Long medicationId,
        @Schema(example = "허리 통증 줄여주는 약") String name,
        @Schema(example = "https://.../pill.jpg") String imageUrl,
        @Schema(example = "파란색 원기둥") String appearance,
        @Schema(example = "약 상자 위에서 2번째 칸") String storageLocation,
        @Schema(example = "2정") String doseAmount,
        @Schema(description = "TTS로 읽을 문장") String voiceText
) {
}
