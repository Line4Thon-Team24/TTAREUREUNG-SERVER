package com.ttareureung.api.domain.medication.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Locale;

@Getter
@RequiredArgsConstructor
public enum MealTimeSlot {
    MORNING("아침"),
    LUNCH("점심"),
    DINNER("저녁"),
    BEFORE_SLEEP("자기 전");

    private final String label;

    // enum 이름(MORNING)이나 한글 표기(아침, 조식, 취침 전 등)를 enum으로 변환. 모르는 값은 null
    public static MealTimeSlot from(String value) {
        if (value == null) {
            return null;
        }
        String text = value.strip();
        for (MealTimeSlot slot : values()) {
            if (slot.name().equals(text.toUpperCase(Locale.ROOT)) || slot.label.equals(text)) {
                return slot;
            }
        }
        return switch (text.replace(" ", "")) {
            case "아침", "조식", "오전" -> MORNING;
            case "점심", "중식" -> LUNCH;
            case "저녁", "석식", "오후" -> DINNER;
            case "자기전", "취침전", "취침", "잠자기전", "자기전에" -> BEFORE_SLEEP;
            default -> null;
        };
    }
}