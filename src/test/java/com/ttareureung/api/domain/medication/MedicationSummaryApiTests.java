package com.ttareureung.api.domain.medication;

import com.ttareureung.api.support.ApiTestSupport;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MedicationSummaryApiTests extends ApiTestSupport {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Test
    void calendarCountsDaysByStatusAndComputesRatesOverMonthLength() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        long medicationId = registerMedication(f.accountId(), f.seniorId(), "혈압약",
                "2026-11-01", "2026-11-03", "MORNING", "DINNER");

        setStatus(token, medicationId, "MORNING", "2026-11-01", "TAKEN");   // 11-01 전부 복용
        setStatus(token, medicationId, "DINNER", "2026-11-01", "TAKEN");
        setStatus(token, medicationId, "MORNING", "2026-11-02", "TAKEN");   // 11-02 일부 복용
        setStatus(token, medicationId, "MORNING", "2026-11-03", "MISSED");  // 11-03 미복용

        getAs(token, calendarUrl(f.seniorId(), "2026-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.month").value("2026-11"))
                .andExpect(jsonPath("$.data.days_in_month").value(30))
                .andExpect(jsonPath("$.data.completed_days").value(1))
                .andExpect(jsonPath("$.data.partial_days").value(1))
                .andExpect(jsonPath("$.data.missed_days").value(1))
                .andExpect(jsonPath("$.data.rate").value(7))
                .andExpect(jsonPath("$.data.completed_rate").value(3))
                .andExpect(jsonPath("$.data.partial_rate").value(3))
                .andExpect(jsonPath("$.data.missed_rate").value(3))
                .andExpect(jsonPath("$.data.days.length()").value(3))
                .andExpect(jsonPath("$.data.days[0].date").value("2026-11-01"))
                .andExpect(jsonPath("$.data.days[0].status").value("TAKEN"))
                .andExpect(jsonPath("$.data.days[0].total_count").value(2))
                .andExpect(jsonPath("$.data.days[0].taken_count").value(2))
                .andExpect(jsonPath("$.data.days[0].medicines[0]").value("혈압약"))
                .andExpect(jsonPath("$.data.days[1].status").value("PARTIAL"))
                .andExpect(jsonPath("$.data.days[2].status").value("MISSED"));
    }

    @Test
    void emptyMonthHasNoDaysAndUsesRealMonthLength() throws Exception {
        Family f = newFamily();

        getAs(bearer(f.accountId()), calendarUrl(f.seniorId(), "2028-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days_in_month").value(29))
                .andExpect(jsonPath("$.data.rate").value(0))
                .andExpect(jsonPath("$.data.days.length()").value(0));
    }

    @Test
    void calendarRejectsInvalidMonthAndOtherAccounts() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());

        getAs(token, calendarUrl(f.seniorId(), "2026-13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
        getAs(token, calendarUrl(f.seniorId(), "abc")).andExpect(status().isBadRequest());
        getAs(bearer(newAccount()), calendarUrl(f.seniorId(), "2026-11"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_002"));
        mockMvc.perform(get(calendarUrl(f.seniorId(), "2026-11"))).andExpect(status().isUnauthorized());
    }

    @Test
    void summaryCountsRegisteredAndActiveMedicationsPerSenior() throws Exception {
        Family f = newFamily();
        long senior2 = createSenior(f.accountId(), "박종옥", f.guardianId());
        LocalDate today = LocalDate.now(KST);
        registerMedication(f.accountId(), f.seniorId(), "약A", today.toString(), today.plusDays(1).toString(), "MORNING");
        registerMedication(f.accountId(), f.seniorId(), "약B", today.minusDays(1).toString(), today.toString(), "LUNCH");
        registerMedication(f.accountId(), f.seniorId(), "종료된 약", today.minusDays(5).toString(), today.minusDays(3).toString(), "DINNER");

        getAs(bearer(f.accountId()), "/api/medications/summary?senior_ids=" + f.seniorId() + "," + senior2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seniors.length()").value(2))
                .andExpect(jsonPath("$.data.seniors[0].senior_id").value(f.seniorId()))
                .andExpect(jsonPath("$.data.seniors[0].registered").value(true))
                .andExpect(jsonPath("$.data.seniors[0].medication_count").value(2))
                .andExpect(jsonPath("$.data.seniors[1].senior_id").value(senior2))
                .andExpect(jsonPath("$.data.seniors[1].registered").value(false))
                .andExpect(jsonPath("$.data.seniors[1].medication_count").value(0));
    }

    @Test
    void summaryRejectsOtherAccountsTooManyIdsAndMissingToken() throws Exception {
        Family mine = newFamily();
        Family other = newFamily();
        String token = bearer(mine.accountId());

        getAs(token, "/api/medications/summary?senior_ids=" + mine.seniorId() + "," + other.seniorId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_002"));
        String tooMany = LongStream.rangeClosed(1, 51).mapToObj(String::valueOf).collect(Collectors.joining(","));
        getAs(token, "/api/medications/summary?senior_ids=" + tooMany)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
        mockMvc.perform(get("/api/medications/summary?senior_ids=1")).andExpect(status().isUnauthorized());
    }

    private void setStatus(String token, long medicationId, String slot, String date, String status) throws Exception {
        patchJson(token, "/api/medications/logs/" + logId(medicationId, slot, date), Map.of("status", status))
                .andExpect(status().isOk());
    }

    private String calendarUrl(long seniorId, String month) {
        return "/api/medications/calendar?senior_id=" + seniorId + "&month=" + month;
    }
}
