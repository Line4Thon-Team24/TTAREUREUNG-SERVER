package com.ttareureung.api.domain.senior.dto;

import com.ttareureung.api.domain.medication.entity.MealTimeSlot;
import com.ttareureung.api.domain.senior.entity.GuardianAlert;
import com.ttareureung.api.domain.senior.entity.GuardianAlertType;
import com.ttareureung.api.domain.senior.service.VoiceText;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 보호자 화면 상단 알림 한 건. "김순자님께서 오후 3시 약 복용을 완료했어요."
public record GuardianAlertResponse(
        @Schema(example = "41") Long alertId,
        GuardianAlertType type,
        @Schema(example = "2") Long seniorId,
        @Schema(example = "김순자") String seniorName,
        @Schema(example = "15") Long sessionId,
        MealTimeSlot timeSlot,
        @Schema(example = "점심 식후") String slotName,
        @Schema(example = "2026-11-08") LocalDate targetDate,
        @Schema(example = "2026-11-08T15:00:00") LocalDateTime scheduledAt,
        @Schema(description = "화면 문구") String message,
        @Schema(example = "2026-11-08T15:22:00") LocalDateTime createdAt,
        @Schema(description = "null이면 안 읽음") LocalDateTime readAt
) {
    public static GuardianAlertResponse from(GuardianAlert a) {
        var s = a.getSession();
        String name = a.getSenior().getName();
        String time = VoiceText.koreanTime(s.getScheduledAt());
        String message = switch (a.getType()) {
            case COMPLETED -> name + "님께서 " + time + " 약 복용을 완료했어요.";
            case PARTIAL -> name + "님께서 " + time + " 약을 일부만 드셨어요.";
            case MISSED -> name + "님께서 " + time + " 약을 드시지 않았어요.";
            case HELP_REQUEST -> name + "님께서 약 복용 중 도움을 요청했어요.";
        };
        return new GuardianAlertResponse(a.getId(), a.getType(), a.getSenior().getId(), name, s.getId(),
                s.getTimeSlot(), s.getTimeSlot().getLabel(), s.getTargetDate(), s.getScheduledAt(),
                message, a.getCreatedAt(), a.getReadAt());
    }
}
