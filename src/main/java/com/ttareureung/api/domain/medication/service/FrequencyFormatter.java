package com.ttareureung.api.domain.medication.service;

import com.ttareureung.api.domain.medication.entity.IntakeTiming;

public final class FrequencyFormatter {
    private FrequencyFormatter() {}

    public static String compose(int intervalDays, IntakeTiming timing) {
        String interval = switch (intervalDays) {
            case 1 -> "매일";
            case 2 -> "이틀에 한 번";
            case 7 -> "일주일에 한 번";
            default -> intervalDays + "일에 한 번";
        };
        return timing == null ? interval : interval + " / " + timing.getLabel();
    }
}
