package com.ttareureung.api.domain.medication.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum IntakeTiming {
    BEFORE_MEAL_30M("식사 30분 전"),
    AFTER_MEAL_30M("식사 30분 후"),
    RIGHT_AFTER_MEAL("식사 직후"),
    BEFORE_BED("취침 전");

    private final String label;
}
