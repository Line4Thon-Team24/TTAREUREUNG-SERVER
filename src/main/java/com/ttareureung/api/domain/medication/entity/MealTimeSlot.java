package com.ttareureung.api.domain.medication.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MealTimeSlot {
    MORNING("아침"),
    LUNCH("점심"),
    DINNER("저녁"),
    BEFORE_SLEEP("자기 전");

    private final String label;
}
