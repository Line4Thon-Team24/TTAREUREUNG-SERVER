package com.ttareureung.api.domain.medication.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

public record MonthlyCalendarResponse(
        String month,
        @JsonProperty("days_in_month") int daysInMonth,
        @JsonProperty("completed_days") int completedDays,
        @JsonProperty("partial_days") int partialDays,
        @JsonProperty("missed_days") int missedDays,
        int rate,
        @JsonProperty("completed_rate") int completedRate,
        @JsonProperty("partial_rate") int partialRate,
        @JsonProperty("missed_rate") int missedRate,
        List<Day> days
) {
    public record Day(LocalDate date,
                      String status,
                      @JsonProperty("total_count") int totalCount,
                      @JsonProperty("taken_count") int takenCount,
                      List<String> medicines) {}
}
