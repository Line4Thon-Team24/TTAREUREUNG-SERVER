package com.ttareureung.api.domain.senior.dto;

import com.ttareureung.api.domain.senior.entity.DoseSessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

// 결과 화면. status가 SCHEDULED면 "10분 뒤 재전화" 대기
public record FinishResponse(
        @Schema(example = "15") Long sessionId,
        @Schema(description = "COMPLETED / PARTIAL / MISSED, 또는 재전화 대기 SCHEDULED") DoseSessionStatus status,
        @Schema(example = "3") int takenCount,
        @Schema(example = "5") int totalCount,
        @Schema(description = "일정이 하루 늘어난 약 수", example = "2") int extendedMedicationCount,
        @Schema(description = "보호자 알림 생성 여부") boolean guardianNotified,
        @Schema(description = "재전화 대기면 같은 회차, 아니면 다음 회차. 없으면 null") NextCallInfo nextCall,
        @Schema(description = "TTS로 읽을 문장") String voiceText
) {
}
