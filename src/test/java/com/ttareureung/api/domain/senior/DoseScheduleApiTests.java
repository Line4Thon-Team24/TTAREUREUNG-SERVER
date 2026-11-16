package com.ttareureung.api.domain.senior;

import com.ttareureung.api.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DoseScheduleApiTests extends ApiTestSupport {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

    @Test
    void returnsDefaultDoseTimesForNewSenior() throws Exception {
        Family f = newFamily();

        getAs(bearer(f.accountId()), doseTimesUrl(f.seniorId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seniorId").value(f.seniorId()))
                .andExpect(jsonPath("$.data.doseTimes.length()").value(4))
                .andExpect(jsonPath("$.data.doseTimes[0].timeSlot").value("MORNING"))
                .andExpect(jsonPath("$.data.doseTimes[0].slotName").value("아침 식후"))
                .andExpect(jsonPath("$.data.doseTimes[0].doseTime").value("08:00"))
                .andExpect(jsonPath("$.data.doseTimes[1].doseTime").value("12:30"))
                .andExpect(jsonPath("$.data.doseTimes[2].doseTime").value("18:30"))
                .andExpect(jsonPath("$.data.doseTimes[3].timeSlot").value("BEFORE_SLEEP"))
                .andExpect(jsonPath("$.data.doseTimes[3].doseTime").value("22:00"));
        assertThat(count("SELECT COUNT(*) FROM senior_dose_times WHERE senior_profile_id = ?", f.seniorId())).isZero();
    }

    @Test
    void updatesSentSlotsAndKeepsOthers() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());

        putDoseTimes(token, f.seniorId(), List.of(slot("MORNING", "09:00"), slot("LUNCH", "13:00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.doseTimes[0].doseTime").value("09:00"))
                .andExpect(jsonPath("$.data.doseTimes[1].doseTime").value("13:00"))
                .andExpect(jsonPath("$.data.doseTimes[2].doseTime").value("18:30"))
                .andExpect(jsonPath("$.data.doseTimes[3].doseTime").value("22:00"));
        assertThat(count("SELECT COUNT(*) FROM senior_dose_times WHERE senior_profile_id = ?", f.seniorId())).isEqualTo(2);

        // 같은 회차 재설정은 행 추가가 아니라 갱신
        putDoseTimes(token, f.seniorId(), List.of(slot("MORNING", "09:30"))).andExpect(status().isOk());
        assertThat(count("SELECT COUNT(*) FROM senior_dose_times WHERE senior_profile_id = ?", f.seniorId())).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT dose_time FROM senior_dose_times WHERE senior_profile_id = ? AND time_slot = 'MORNING'",
                String.class, f.seniorId())).isEqualTo("09:30");

        getAs(token, doseTimesUrl(f.seniorId()))
                .andExpect(jsonPath("$.data.doseTimes[0].doseTime").value("09:30"))
                .andExpect(jsonPath("$.data.doseTimes[1].doseTime").value("13:00"));
    }

    @Test
    void rejectsInvalidDoseTimeRequests() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());

        putDoseTimes(token, f.seniorId(), List.of(slot("MORNING", "09:00"), slot("MORNING", "10:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SENIOR_002"));

        // 아침을 기본 점심(12:30) 뒤로 보내면 순서 위반
        putDoseTimes(token, f.seniorId(), List.of(slot("MORNING", "13:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SENIOR_001"));

        putDoseTimes(token, f.seniorId(), List.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));

        putDoseTimes(token, f.seniorId(), List.of(slot("MORNING", "25:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));

        putDoseTimes(token, f.guardianId(), List.of(slot("MORNING", "09:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEDICATION_003"));

        Family other = newFamily();
        putDoseTimes(bearer(other.accountId()), f.seniorId(), List.of(slot("MORNING", "09:00")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_002"));
        getAs(bearer(other.accountId()), seniorScheduleUrl(f.seniorId(), null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_002"));

        mockMvc.perform(get(doseTimesUrl(f.seniorId()))).andExpect(status().isUnauthorized());
        assertThat(count("SELECT COUNT(*) FROM senior_dose_times WHERE senior_profile_id = ?", f.seniorId())).isZero();
    }

    @Test
    void todayScheduleCreatesSessionsOnlyForSlotsWithMedications() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        String today = TODAY.toString();
        registerMedication(f.accountId(), f.seniorId(), "관절약", today, TODAY.plusDays(1).toString(), "MORNING", "LUNCH");
        registerMedication(f.accountId(), f.seniorId(), "혈압약", today, today, "DINNER");

        ResultActions first = getAs(token, seniorScheduleUrl(f.seniorId(), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value(today))
                .andExpect(jsonPath("$.data.today").value(true))
                .andExpect(jsonPath("$.data.takenCount").value(0))
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andExpect(jsonPath("$.data.sessions.length()").value(3))
                .andExpect(jsonPath("$.data.sessions[0].timeSlot").value("MORNING"))
                .andExpect(jsonPath("$.data.sessions[0].sessionId").isNumber())
                .andExpect(jsonPath("$.data.sessions[0].slotName").value("아침 식후"))
                .andExpect(jsonPath("$.data.sessions[0].doseTime").value("08:00"))
                .andExpect(jsonPath("$.data.sessions[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.sessions[0].answeredAt").doesNotExist())
                .andExpect(jsonPath("$.data.sessions[0].medications[0].name").value("관절약"))
                .andExpect(jsonPath("$.data.sessions[0].medications[0].doseAmount").value("1정"))
                .andExpect(jsonPath("$.data.sessions[0].medications[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.sessions[2].timeSlot").value("DINNER"))
                .andExpect(jsonPath("$.data.sessions[2].medications[0].name").value("혈압약"));
        long morningSessionId = jsonMapper.readTree(first.andReturn().getResponse().getContentAsString())
                .path("data").path("sessions").get(0).path("sessionId").asLong();
        assertThat(count("SELECT COUNT(*) FROM dose_sessions WHERE senior_profile_id = ?", f.seniorId())).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM dose_sessions WHERE senior_profile_id = ? AND status = 'SCHEDULED' AND call_attempts = 0",
                f.seniorId())).isEqualTo(3);

        // 두 번째 조회는 같은 회차 재사용
        getAs(token, seniorScheduleUrl(f.seniorId(), today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessions[0].sessionId").value(morningSessionId));
        assertThat(count("SELECT COUNT(*) FROM dose_sessions WHERE senior_profile_id = ?", f.seniorId())).isEqualTo(3);
    }

    @Test
    void otherDatesAreComputedWithoutCreatingSessions() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        String tomorrow = TODAY.plusDays(1).toString();
        String yesterday = TODAY.minusDays(1).toString();
        registerMedication(f.accountId(), f.seniorId(), "관절약", yesterday, tomorrow, "MORNING", "LUNCH");

        getAs(token, seniorScheduleUrl(f.seniorId(), tomorrow))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.today").value(false))
                .andExpect(jsonPath("$.data.sessions.length()").value(2))
                .andExpect(jsonPath("$.data.sessions[0].sessionId").doesNotExist())
                .andExpect(jsonPath("$.data.sessions[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.sessions[1].timeSlot").value("LUNCH"));

        getAs(token, seniorScheduleUrl(f.seniorId(), yesterday))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessions[0].status").value("MISSED"))
                .andExpect(jsonPath("$.data.sessions[1].status").value("MISSED"));
        assertThat(count("SELECT COUNT(*) FROM dose_sessions WHERE senior_profile_id = ?", f.seniorId())).isZero();

        // 보호자가 기록을 직접 바꾼 경우도 상태에 반영
        jdbcTemplate.update("UPDATE medication_logs SET status = 'TAKEN' WHERE target_date = ? AND meal_time_slot = 'MORNING'", yesterday);
        getAs(token, seniorScheduleUrl(f.seniorId(), yesterday))
                .andExpect(jsonPath("$.data.sessions[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.sessions[0].medications[0].status").value("TAKEN"))
                .andExpect(jsonPath("$.data.takenCount").value(1))
                .andExpect(jsonPath("$.data.totalCount").value(2));

        getAs(token, seniorScheduleUrl(f.seniorId(), "2026-13-40"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    void emptyScheduleWhenNoMedications() throws Exception {
        Family f = newFamily();

        getAs(bearer(f.accountId()), seniorScheduleUrl(f.seniorId(), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.today").value(true))
                .andExpect(jsonPath("$.data.takenCount").value(0))
                .andExpect(jsonPath("$.data.totalCount").value(0))
                .andExpect(jsonPath("$.data.sessions.length()").value(0));
        assertThat(count("SELECT COUNT(*) FROM dose_sessions WHERE senior_profile_id = ?", f.seniorId())).isZero();
    }

    @Test
    void concurrentTodayRequestsCreateEachSessionOnce() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        String today = TODAY.toString();
        registerMedication(f.accountId(), f.seniorId(), "관절약", today, today, "MORNING", "LUNCH", "DINNER");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> request = () -> {
            start.await();
            return getAs(token, seniorScheduleUrl(f.seniorId(), null)).andReturn().getResponse().getStatus();
        };
        try {
            List<Future<Integer>> futures = List.of(pool.submit(request), pool.submit(request));
            start.countDown();
            for (Future<Integer> future : futures) {
                assertThat(future.get()).isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(count("SELECT COUNT(*) FROM dose_sessions WHERE senior_profile_id = ?", f.seniorId())).isEqualTo(3);
    }

    @Test
    void changingDoseTimeMovesTodaysPendingSession() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        String today = TODAY.toString();
        registerMedication(f.accountId(), f.seniorId(), "관절약", today, today, "MORNING", "LUNCH");
        getAs(token, seniorScheduleUrl(f.seniorId(), null))
                .andExpect(jsonPath("$.data.sessions[0].doseTime").value("08:00"));

        // 전화 전 회차만 이동. 이미 진행 중인 회차는 그대로
        jdbcTemplate.update("UPDATE dose_sessions SET status = 'IN_PROGRESS' WHERE senior_profile_id = ? AND time_slot = 'LUNCH'", f.seniorId());
        putDoseTimes(token, f.seniorId(), List.of(slot("MORNING", "09:00"), slot("LUNCH", "14:00")))
                .andExpect(status().isOk());

        getAs(token, seniorScheduleUrl(f.seniorId(), null))
                .andExpect(jsonPath("$.data.sessions[0].doseTime").value("09:00"))
                .andExpect(jsonPath("$.data.sessions[1].doseTime").value("12:30"))
                .andExpect(jsonPath("$.data.sessions[1].status").value("IN_PROGRESS"));
    }

    @Test
    void deletingSeniorRemovesDoseTimesAndSessions() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        String today = TODAY.toString();
        registerMedication(f.accountId(), f.seniorId(), "관절약", today, today, "MORNING");
        putDoseTimes(token, f.seniorId(), List.of(slot("MORNING", "09:00"))).andExpect(status().isOk());
        getAs(token, seniorScheduleUrl(f.seniorId(), null)).andExpect(status().isOk());
        assertThat(count("SELECT COUNT(*) FROM dose_sessions WHERE senior_profile_id = ?", f.seniorId())).isEqualTo(1);

        deleteAs(token, "/api/v1/profiles/" + f.seniorId()).andExpect(status().is2xxSuccessful());

        assertThat(count("SELECT COUNT(*) FROM dose_sessions WHERE senior_profile_id = ?", f.seniorId())).isZero();
        assertThat(count("SELECT COUNT(*) FROM senior_dose_times WHERE senior_profile_id = ?", f.seniorId())).isZero();
        assertThat(count("SELECT COUNT(*) FROM profiles WHERE id = ?", f.seniorId())).isZero();
    }

    private static Map<String, String> slot(String timeSlot, String doseTime) {
        return Map.of("timeSlot", timeSlot, "doseTime", doseTime);
    }

    private ResultActions putDoseTimes(String token, long seniorId, List<Map<String, String>> items) throws Exception {
        return putJson(token, doseTimesUrl(seniorId), Map.of("doseTimes", items));
    }

    private static String doseTimesUrl(long seniorId) {
        return "/api/v1/seniors/" + seniorId + "/dose-times";
    }

    private static String seniorScheduleUrl(long seniorId, String date) {
        return "/api/v1/seniors/" + seniorId + "/schedules" + (date == null ? "" : "?date=" + date);
    }
}
