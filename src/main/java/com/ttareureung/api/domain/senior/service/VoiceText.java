package com.ttareureung.api.domain.senior.service;

import com.ttareureung.api.domain.medication.entity.MealTimeSlot;
import com.ttareureung.api.domain.medication.entity.Medication;
import com.ttareureung.api.domain.senior.entity.RecognitionResult;

import java.util.List;

/**
 * 띠링이가 읽어 줄 문장. 프론트는 이 문장을 그대로 TTS로 재생
 * 서버에서 만드는 이유: 약 이름·보관 위치·수량 조합 규칙을 한 곳에서 관리
 */
public final class VoiceText {

    private static final List<String> ORDINALS = List.of(
            "첫 번째", "두 번째", "세 번째", "네 번째", "다섯 번째", "여섯 번째", "일곱 번째", "여덟 번째", "아홉 번째", "열 번째");

    private VoiceText() {
    }

    public static String callGreeting(MealTimeSlot slot, int medicationCount) {
        return slot.getLabel() + " 약 드실 시간이에요. 약이 " + medicationCount + "개 있어요.";
    }

    // "첫 번째 약이에요. 허리 통증 줄여주는 약, 약 상자 위에서 2번째 칸에 있어요. 파란색 원기둥이에요. 2정 드세요."
    public static String findMedication(int order, Medication medication) {
        StringBuilder sb = new StringBuilder();
        sb.append(ordinal(order)).append(" 약이에요. ").append(medication.getDisplayName());
        if (hasText(medication.getStorageLocation())) {
            sb.append(", ").append(medication.getStorageLocation()).append("에 있어요.");
        } else {
            sb.append("이에요.");
        }
        if (hasText(medication.getAppearance())) {
            sb.append(" ").append(medication.getAppearance()).append("이에요.");
        }
        if (hasText(medication.getDoseAmount())) {
            sb.append(" ").append(medication.getDoseAmount()).append(" 드세요.");
        }
        return sb.toString();
    }

    public static String recognition(RecognitionResult result) {
        return switch (result) {
            case MATCH -> "맞는 약이에요!";
            case MISMATCH -> "다른 약이에요. 다시 찾아주세요.";
            case UNCERTAIN -> "확실하지 않아요. 사진을 보고 골라 주세요.";
        };
    }

    // 복용 처리 후. 남은 약이 있으면 다음 약 찾기로 유도
    public static String afterIntake(int remaining) {
        return remaining > 0 ? "잘 드셨어요. 다음 약을 찾아볼까요?" : "모든 약을 다 드셨어요.";
    }

    private static String ordinal(int order) {
        return order >= 1 && order <= ORDINALS.size() ? ORDINALS.get(order - 1) : order + "번째";
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
