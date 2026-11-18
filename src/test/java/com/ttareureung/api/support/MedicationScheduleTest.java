package com.ttareureung.api.support;

import com.ttareureung.api.domain.medication.entity.Medication;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MedicationScheduleTest {

    private Medication medication(String start, String end, Integer interval) {
        return Medication.builder().seniorProfileId(1L).drugName("약")
                .startDate(LocalDate.parse(start)).endDate(LocalDate.parse(end)).intervalDays(interval).build();
    }

    @Test
    void scheduledOnlyOnIntervalDaysInsideThePeriod() {
        Medication m = medication("2026-11-01", "2026-11-07", 2);

        assertThat(m.isScheduledOn(LocalDate.parse("2026-11-01"))).isTrue();
        assertThat(m.isScheduledOn(LocalDate.parse("2026-11-02"))).isFalse();
        assertThat(m.isScheduledOn(LocalDate.parse("2026-11-07"))).isTrue();
        assertThat(m.isScheduledOn(LocalDate.parse("2026-11-09"))).isFalse(); // 기간 밖
        assertThat(m.isScheduledOn(LocalDate.parse("2026-10-30"))).isFalse();
    }

    @Test
    void extendAddsExactlyOneMoreOccurrence() {
        assertThat(medication("2026-11-01", "2026-11-07", 1).extendToNextOccurrence())
                .isEqualTo(LocalDate.parse("2026-11-08"));
        assertThat(medication("2026-11-01", "2026-11-07", 2).extendToNextOccurrence())
                .isEqualTo(LocalDate.parse("2026-11-09"));
        assertThat(medication("2026-11-01", "2026-11-08", 2).extendToNextOccurrence()) // 종료일이 복용일이 아닌 경우
                .isEqualTo(LocalDate.parse("2026-11-09"));
    }

    @Test
    void missingIntervalMeansEveryDay() {
        Medication m = medication("2026-11-01", "2026-11-03", null);

        assertThat(m.getIntervalDays()).isEqualTo(1);
        assertThat(m.isScheduledOn(LocalDate.parse("2026-11-02"))).isTrue();
    }
}
