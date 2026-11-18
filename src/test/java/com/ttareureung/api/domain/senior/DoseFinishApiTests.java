package com.ttareureung.api.domain.senior;

import com.ttareureung.api.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DoseFinishApiTests extends ApiTestSupport {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.now(KST);
    private static final String D = TODAY.toString();
    private static final String TOMORROW = TODAY.plusDays(1).toString();
    private static final String ALERTS = "/api/v1/guardian-alerts";

    @Test
    void completedFinishCreatesAlertAndPointsToNextCall() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        long morning = registerMedication(f.accountId(), f.seniorId(), "허리약", D, D, "MORNING");
        registerMedication(f.accountId(), f.seniorId(), "저녁약", D, D, "DINNER");
        passAllTimes(token, f.seniorId());
        long sessionId = currentSessionId(token, f.seniorId());
        answer(token, sessionId).andExpect(status().isOk());
        intake(token, sessionId, morning).andExpect(status().isOk());

        finish(token, sessionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.takenCount").value(1))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.extendedMedicationCount").value(0))
                .andExpect(jsonPath("$.data.guardianNotified").value(true))
                .andExpect(jsonPath("$.data.nextCall.timeSlot").value("DINNER"))
                .andExpect(jsonPath("$.data.nextCall.medicationNames[0]").value("저녁약"))
                .andExpect(jsonPath("$.data.voiceText").value(containsString("다음 전화는 오전 12시 2분")));
        assertThat(count("SELECT COUNT(*) FROM guardian_alerts WHERE dose_session_id = ? AND type = 'COMPLETED'", sessionId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT finished_at FROM dose_sessions WHERE id = ?", String.class, sessionId)).isNotNull();

        finish(token, sessionId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SENIOR_004"));
        getAs(token, "/api/v1/seniors/" + f.seniorId() + "/schedules")
                .andExpect(jsonPath("$.data.sessions[0].status").value("COMPLETED"));
    }

    @Test
    void partialFinishExtendsOnlyMissedMedications() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        long taken = registerMedication(f.accountId(), f.seniorId(), "허리약", D, D, "MORNING");
        long missed = registerMedication(f.accountId(), f.seniorId(), "혈압약", D, D, "MORNING", "DINNER");
        passAllTimes(token, f.seniorId());
        long sessionId = currentSessionId(token, f.seniorId());
        answer(token, sessionId).andExpect(status().isOk());
        intake(token, sessionId, taken).andExpect(status().isOk());

        finish(token, sessionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PARTIAL"))
                .andExpect(jsonPath("$.data.takenCount").value(1))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.extendedMedicationCount").value(1))
                .andExpect(jsonPath("$.data.guardianNotified").value(true))
                .andExpect(jsonPath("$.data.voiceText").value("못 드신 약 1개는 일정이 하루 늘어났어요."));

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM medication_logs WHERE medication_id = ? AND target_date = ? AND meal_time_slot = 'MORNING'",
                String.class, missed, D)).isEqualTo("MISSED");
        assertThat(jdbcTemplate.queryForObject("SELECT end_date FROM medication WHERE id = ?", String.class, missed)).isEqualTo(TOMORROW);
        assertThat(jdbcTemplate.queryForObject("SELECT end_date FROM medication WHERE id = ?", String.class, taken)).isEqualTo(D);
        // 늘어난 날짜에는 그 약이 원래 가진 회차(아침·저녁) 기록이 생김
        assertThat(count("SELECT COUNT(*) FROM medication_logs WHERE medication_id = ? AND target_date = ?", missed, TOMORROW)).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM medication_logs WHERE medication_id = ? AND target_date = ?", taken, TOMORROW)).isZero();
        assertThat(count("SELECT COUNT(*) FROM guardian_alerts WHERE dose_session_id = ? AND type = 'PARTIAL'", sessionId)).isEqualTo(1);

        getAs(token, "/api/v1/seniors/" + f.seniorId() + "/schedules?date=" + TOMORROW)
                .andExpect(jsonPath("$.data.sessions.length()").value(2))
                .andExpect(jsonPath("$.data.sessions[0].medications.length()").value(1))
                .andExpect(jsonPath("$.data.sessions[0].medications[0].name").value("혈압약"));
    }

    @Test
    void zeroTakenFinishRetriesTwiceThenMisses() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        long medicationId = registerMedication(f.accountId(), f.seniorId(), "허리약", D, D, "MORNING");
        passAllTimes(token, f.seniorId());
        long sessionId = currentSessionId(token, f.seniorId());

        answer(token, sessionId).andExpect(status().isOk());
        finish(token, sessionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.extendedMedicationCount").value(0))
                .andExpect(jsonPath("$.data.guardianNotified").value(true))
                .andExpect(jsonPath("$.data.nextCall.timeSlot").value("MORNING"))
                .andExpect(jsonPath("$.data.nextCall.scheduledAt").isNotEmpty())
                .andExpect(jsonPath("$.data.voiceText").value(containsString("10분 뒤에 다시 전화")));
        assertThat(count("SELECT call_attempts FROM dose_sessions WHERE id = ?", sessionId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM guardian_alerts WHERE dose_session_id = ? AND type = 'MISSED'", sessionId)).isEqualTo(1);
        // 재전화 시각 전에는 수신화면에 안 뜸
        getAs(token, currentUrl(f.seniorId())).andExpect(jsonPath("$.data.current").doesNotExist());

        // 10분이 지난 것처럼 재전화 시각을 당김 → 다시 받고 또 안 먹음
        jdbcTemplate.update("UPDATE dose_sessions SET next_call_at = UTC_TIMESTAMP(6) WHERE id = ?", sessionId);
        getAs(token, currentUrl(f.seniorId())).andExpect(jsonPath("$.data.current.sessionId").value(sessionId));
        answer(token, sessionId).andExpect(status().isOk());
        finish(token, sessionId).andExpect(jsonPath("$.data.status").value("SCHEDULED"));
        assertThat(count("SELECT COUNT(*) FROM guardian_alerts WHERE dose_session_id = ? AND type = 'MISSED'", sessionId)).isEqualTo(1);

        jdbcTemplate.update("UPDATE dose_sessions SET next_call_at = UTC_TIMESTAMP(6) WHERE id = ?", sessionId);
        answer(token, sessionId).andExpect(status().isOk());
        finish(token, sessionId)
                .andExpect(jsonPath("$.data.status").value("MISSED"))
                .andExpect(jsonPath("$.data.extendedMedicationCount").value(1))
                // 연장된 약 때문에 다음 전화는 내일 아침
                .andExpect(jsonPath("$.data.nextCall.scheduledAt").value(TOMORROW + "T00:00:00"))
                .andExpect(jsonPath("$.data.voiceText").value(containsString("오늘은 약을 못 드셨어요")));
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM medication_logs WHERE medication_id = ? AND target_date = ?", String.class, medicationId, D)).isEqualTo("MISSED");
        assertThat(jdbcTemplate.queryForObject("SELECT end_date FROM medication WHERE id = ?", String.class, medicationId)).isEqualTo(TOMORROW);
        assertThat(count("SELECT COUNT(*) FROM guardian_alerts WHERE dose_session_id = ? AND type = 'MISSED'", sessionId)).isEqualTo(1);
    }

    @Test
    void threeNoAnswersAlsoExtendAndAlert() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        long medicationId = registerMedication(f.accountId(), f.seniorId(), "허리약", D, D, "MORNING");
        passAllTimes(token, f.seniorId());
        long sessionId = currentSessionId(token, f.seniorId());

        for (int i = 0; i < 3; i++) {
            postJson(token, "/api/v1/dose-sessions/" + sessionId + "/no-answer", "").andExpect(status().isOk());
        }
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM dose_sessions WHERE id = ?", String.class, sessionId)).isEqualTo("MISSED");
        assertThat(jdbcTemplate.queryForObject("SELECT end_date FROM medication WHERE id = ?", String.class, medicationId)).isEqualTo(TOMORROW);
        assertThat(count("SELECT COUNT(*) FROM medication_logs WHERE medication_id = ? AND target_date = ?", medicationId, TOMORROW)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM guardian_alerts WHERE dose_session_id = ? AND type = 'MISSED'", sessionId)).isEqualTo(1);
    }

    @Test
    void helpCreatesAlertUntilSessionIsFinished() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        long medicationId = registerMedication(f.accountId(), f.seniorId(), "허리약", D, D, "MORNING");
        passAllTimes(token, f.seniorId());
        long sessionId = currentSessionId(token, f.seniorId());

        // 전화 받기 전에도 도움 요청 가능
        help(token, sessionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alertId").isNumber())
                .andExpect(jsonPath("$.data.sessionId").value(sessionId))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.voiceText").value("보호자께 도움을 요청했어요. 잠시만 기다려 주세요."));
        answer(token, sessionId).andExpect(status().isOk());
        help(token, sessionId).andExpect(status().isOk());
        assertThat(count("SELECT COUNT(*) FROM guardian_alerts WHERE dose_session_id = ? AND type = 'HELP_REQUEST'", sessionId)).isEqualTo(2);

        intake(token, sessionId, medicationId).andExpect(status().isOk());
        finish(token, sessionId).andExpect(jsonPath("$.data.status").value("COMPLETED"));
        help(token, sessionId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SENIOR_004"));

        Family other = newFamily();
        help(bearer(other.accountId()), sessionId).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SENIOR_003"));
        finish(bearer(other.accountId()), sessionId).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SENIOR_003"));
        mockMvc.perform(post("/api/v1/dose-sessions/" + sessionId + "/finish")).andExpect(status().isUnauthorized());
    }

    @Test
    void finishRequiresAnsweredSession() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        registerMedication(f.accountId(), f.seniorId(), "허리약", D, D, "MORNING");
        passAllTimes(token, f.seniorId());
        long sessionId = currentSessionId(token, f.seniorId());

        finish(token, sessionId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SENIOR_008"));
        assertThat(count("SELECT COUNT(*) FROM guardian_alerts WHERE dose_session_id = ?", sessionId)).isZero();
    }

    @Test
    void listsFiltersAndReadsGuardianAlerts() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        long medicationId = registerMedication(f.accountId(), f.seniorId(), "허리약", D, D, "MORNING");
        passAllTimes(token, f.seniorId());
        long sessionId = currentSessionId(token, f.seniorId());
        answer(token, sessionId).andExpect(status().isOk());
        intake(token, sessionId, medicationId).andExpect(status().isOk());
        finish(token, sessionId).andExpect(jsonPath("$.data.status").value("COMPLETED"));
        answerIgnoringResult(token, sessionId);
        help(token, sessionId).andExpect(status().isConflict()); // 끝난 회차라 생성 안 됨

        // 다른 회차의 도움 요청: 저녁 약을 새로 등록해 회차 생성
        registerMedication(f.accountId(), f.seniorId(), "저녁약", D, D, "DINNER");
        getAs(token, currentUrl(f.seniorId())).andExpect(status().isOk());
        long dinnerSession = jdbcTemplate.queryForObject(
                "SELECT id FROM dose_sessions WHERE senior_profile_id = ? AND time_slot = 'DINNER'", Long.class, f.seniorId());
        help(token, dinnerSession).andExpect(status().isOk());

        ResultActions list = getAs(token, ALERTS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alerts.length()").value(2))
                .andExpect(jsonPath("$.data.alerts[0].type").value("HELP_REQUEST"))
                .andExpect(jsonPath("$.data.alerts[0].seniorName").value("김순자"))
                .andExpect(jsonPath("$.data.alerts[0].message").value("김순자님께서 약 복용 중 도움을 요청했어요."))
                .andExpect(jsonPath("$.data.alerts[0].readAt").doesNotExist())
                .andExpect(jsonPath("$.data.alerts[1].type").value("COMPLETED"))
                .andExpect(jsonPath("$.data.alerts[1].sessionId").value(sessionId))
                .andExpect(jsonPath("$.data.alerts[1].slotName").value("아침"))
                .andExpect(jsonPath("$.data.alerts[1].message").value("김순자님께서 오전 12시 약 복용을 완료했어요."))
                .andExpect(jsonPath("$.data.upcoming.length()").value(0));
        long completedAlert = jsonMapper.readTree(list.andReturn().getResponse().getContentAsString())
                .path("data").path("alerts").get(1).path("alertId").asLong();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(ALERTS + "/" + completedAlert + "/read")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readAt").isNotEmpty());
        getAs(token, ALERTS + "?unreadOnly=true")
                .andExpect(jsonPath("$.data.alerts.length()").value(1))
                .andExpect(jsonPath("$.data.alerts[0].type").value("HELP_REQUEST"));

        // 보호자 프로필의 "복용 완료 알림 OFF"면 완료 알림 제외, 도움 요청은 항상
        patchJson(token, "/api/v1/notification-settings", Map.of("profile_id", f.guardianId(), "notify_on_complete", false))
                .andExpect(status().isOk());
        getAs(token, ALERTS + "?profileId=" + f.guardianId())
                .andExpect(jsonPath("$.data.alerts.length()").value(1))
                .andExpect(jsonPath("$.data.alerts[0].type").value("HELP_REQUEST"));

        Family other = newFamily();
        getAs(bearer(other.accountId()), ALERTS).andExpect(jsonPath("$.data.alerts.length()").value(0));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(ALERTS + "/" + completedAlert + "/read")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, bearer(other.accountId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SENIOR_010"));
        getAs(token, ALERTS + "?profileId=" + other.guardianId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_002"));
        mockMvc.perform(get(ALERTS)).andExpect(status().isUnauthorized());
    }

    @Test
    void upcomingCallsFollowRemindMinutes() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        registerMedication(f.accountId(), f.seniorId(), "점심약", D, D, "LUNCH");
        LocalTime now = LocalTime.now(KST);
        DateTimeFormatter hm = DateTimeFormatter.ofPattern("HH:mm");
        putJson(token, "/api/v1/seniors/" + f.seniorId() + "/dose-times", Map.of("doseTimes", List.of(
                Map.of("timeSlot", "MORNING", "doseTime", now.plusMinutes(5).format(hm)),
                Map.of("timeSlot", "LUNCH", "doseTime", now.plusMinutes(6).format(hm)),
                Map.of("timeSlot", "DINNER", "doseTime", now.plusMinutes(7).format(hm)),
                Map.of("timeSlot", "BEFORE_SLEEP", "doseTime", now.plusMinutes(8).format(hm)))))
                .andExpect(status().isOk());
        getAs(token, currentUrl(f.seniorId())).andExpect(jsonPath("$.data.current").doesNotExist());

        getAs(token, ALERTS + "?profileId=" + f.guardianId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.upcoming.length()").value(1))
                .andExpect(jsonPath("$.data.upcoming[0].seniorName").value("김순자"))
                .andExpect(jsonPath("$.data.upcoming[0].timeSlot").value("LUNCH"))
                .andExpect(jsonPath("$.data.upcoming[0].minutesLeft").value(lessThanOrEqualTo(6)))
                .andExpect(jsonPath("$.data.upcoming[0].minutesLeft").value(greaterThanOrEqualTo(4)));

        patchJson(token, "/api/v1/notification-settings", Map.of("profile_id", f.guardianId(), "remind_before_minutes", 3))
                .andExpect(status().isOk());
        getAs(token, ALERTS + "?profileId=" + f.guardianId())
                .andExpect(jsonPath("$.data.upcoming.length()").value(0));
    }

    @Test
    void deletingSeniorRemovesAlerts() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        registerMedication(f.accountId(), f.seniorId(), "허리약", D, D, "MORNING");
        passAllTimes(token, f.seniorId());
        long sessionId = currentSessionId(token, f.seniorId());
        help(token, sessionId).andExpect(status().isOk());

        deleteAs(token, "/api/v1/profiles/" + f.seniorId()).andExpect(status().is2xxSuccessful());
        assertThat(count("SELECT COUNT(*) FROM guardian_alerts WHERE senior_profile_id = ?", f.seniorId())).isZero();
    }

    private void passAllTimes(String token, long seniorId) throws Exception {
        putJson(token, "/api/v1/seniors/" + seniorId + "/dose-times", Map.of("doseTimes", List.of(
                Map.of("timeSlot", "MORNING", "doseTime", "00:00"), Map.of("timeSlot", "LUNCH", "doseTime", "00:01"),
                Map.of("timeSlot", "DINNER", "doseTime", "00:02"), Map.of("timeSlot", "BEFORE_SLEEP", "doseTime", "00:03"))))
                .andExpect(status().isOk());
    }

    private long currentSessionId(String token, long seniorId) throws Exception {
        return jsonMapper.readTree(getAs(token, currentUrl(seniorId)).andReturn().getResponse().getContentAsString())
                .path("data").path("current").path("sessionId").asLong();
    }

    private ResultActions answer(String token, long sessionId) throws Exception {
        return postJson(token, "/api/v1/dose-sessions/" + sessionId + "/answer", "");
    }

    private void answerIgnoringResult(String token, long sessionId) throws Exception {
        postJson(token, "/api/v1/dose-sessions/" + sessionId + "/answer", "");
    }

    private ResultActions intake(String token, long sessionId, long medicationId) throws Exception {
        return postJson(token, "/api/v1/dose-sessions/" + sessionId + "/medications/" + medicationId + "/intake", "");
    }

    private ResultActions finish(String token, long sessionId) throws Exception {
        return postJson(token, "/api/v1/dose-sessions/" + sessionId + "/finish", "");
    }

    private ResultActions help(String token, long sessionId) throws Exception {
        return postJson(token, "/api/v1/dose-sessions/" + sessionId + "/help", "");
    }

    private static String currentUrl(long seniorId) {
        return "/api/v1/seniors/" + seniorId + "/dose-sessions/current";
    }
}
