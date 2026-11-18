package com.ttareureung.api.domain.senior;

import com.ttareureung.api.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DoseCallApiTests extends ApiTestSupport {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));
    private static final String D = TODAY.toString();

    @Test
    void currentShowsDueSessionWithPendingMedicationsInOrder() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        long first = registerMedication(f.accountId(), f.seniorId(), "허리약", D, D, "MORNING", "LUNCH");
        var body = medicationItem("혈압약", D, D, "MORNING");
        body.put("custom_drug_name", "혈압 내리는 약");
        body.put("dose_amount", "2정");
        body.put("appearance", "파란색 원기둥");
        body.put("senior_profile_id", f.seniorId());
        postJson(token, "/api/v1/medications", body).andExpect(status().isCreated());
        passAllTimes(token, f.seniorId());

        getAs(token, currentUrl(f.seniorId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.serverTime").isNotEmpty())
                .andExpect(jsonPath("$.data.current.sessionId").isNumber())
                .andExpect(jsonPath("$.data.current.timeSlot").value("MORNING"))
                .andExpect(jsonPath("$.data.current.slotName").value("아침"))
                .andExpect(jsonPath("$.data.current.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.current.callAttempts").value(0))
                .andExpect(jsonPath("$.data.current.totalCount").value(2))
                .andExpect(jsonPath("$.data.current.voiceText").value("아침 약 드실 시간이에요. 약이 2개 있어요."))
                .andExpect(jsonPath("$.data.current.medications.length()").value(2))
                .andExpect(jsonPath("$.data.current.medications[0].order").value(1))
                .andExpect(jsonPath("$.data.current.medications[0].medicationId").value(first))
                .andExpect(jsonPath("$.data.current.medications[0].name").value("허리약"))
                .andExpect(jsonPath("$.data.current.medications[0].storageLocation").value("약 상자 위에서 2번째 칸"))
                .andExpect(jsonPath("$.data.current.medications[0].doseAmount").value("1정"))
                .andExpect(jsonPath("$.data.current.medications[0].voiceText")
                        .value("첫 번째 약이에요. 허리약, 약 상자 위에서 2번째 칸에 있어요. 흰색 원형 알약이에요. 1정 드세요."))
                .andExpect(jsonPath("$.data.current.medications[1].order").value(2))
                .andExpect(jsonPath("$.data.current.medications[1].name").value("혈압 내리는 약"))
                .andExpect(jsonPath("$.data.current.medications[1].voiceText").value(containsString("두 번째 약이에요. 혈압 내리는 약")))
                .andExpect(jsonPath("$.data.current.medications[1].voiceText").value(containsString("파란색 원기둥이에요. 2정 드세요.")))
                // 점심 회차도 시각이 지났지만 아침이 먼저. next는 점심
                .andExpect(jsonPath("$.data.next.timeSlot").value("LUNCH"))
                .andExpect(jsonPath("$.data.next.scheduledAt").value(containsString(D)))
                .andExpect(jsonPath("$.data.next.medicationNames[0]").value("허리약"));
        assertThat(count("SELECT COUNT(*) FROM dose_sessions WHERE senior_profile_id = ?", f.seniorId())).isEqualTo(2);
    }

    @Test
    void currentIsNullBeforeScheduledTimeAndNextPointsToIt() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        registerMedication(f.accountId(), f.seniorId(), "허리약", D, D, "LUNCH");
        putDoseTimes(token, f.seniorId(), List.of(slot("LUNCH", "23:57"), slot("DINNER", "23:58"), slot("BEFORE_SLEEP", "23:59")));

        getAs(token, currentUrl(f.seniorId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.current").doesNotExist())
                .andExpect(jsonPath("$.data.next.timeSlot").value("LUNCH"))
                .andExpect(jsonPath("$.data.next.slotName").value("점심"))
                .andExpect(jsonPath("$.data.next.medicationNames[0]").value("허리약"));
    }

    @Test
    void nextFallsBackToTomorrowWhenNothingLeftToday() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        String tomorrow = TODAY.plusDays(1).toString();
        registerMedication(f.accountId(), f.seniorId(), "저녁약", tomorrow, tomorrow, "DINNER");

        getAs(token, currentUrl(f.seniorId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.current").doesNotExist())
                .andExpect(jsonPath("$.data.next.timeSlot").value("DINNER"))
                .andExpect(jsonPath("$.data.next.scheduledAt").value(tomorrow + "T18:30:00"));

        Family empty = newFamily();
        getAs(bearer(empty.accountId()), currentUrl(empty.seniorId()))
                .andExpect(jsonPath("$.data.current").doesNotExist())
                .andExpect(jsonPath("$.data.next").doesNotExist());
    }

    @Test
    void answerStartsSessionAndKeepsFirstAnsweredTime() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        long medicationId = registerMedication(f.accountId(), f.seniorId(), "허리약", D, D, "MORNING");
        registerMedication(f.accountId(), f.seniorId(), "혈압약", D, D, "MORNING");
        passAllTimes(token, f.seniorId());
        long sessionId = currentSessionId(token, f.seniorId());

        answer(token, sessionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(sessionId))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.medications.length()").value(2));
        String answeredAt = jdbcTemplate.queryForObject("SELECT answered_at FROM dose_sessions WHERE id = ?", String.class, sessionId);
        assertThat(answeredAt).isNotNull();

        // 이미 먹은 약은 목록에서 빠지고, 두 번째 호출도 첫 통화 시각 유지
        jdbcTemplate.update("UPDATE medication_logs SET status = 'TAKEN' WHERE medication_id = ? AND target_date = ?", medicationId, D);
        answer(token, sessionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.medications.length()").value(1))
                .andExpect(jsonPath("$.data.medications[0].order").value(1))
                .andExpect(jsonPath("$.data.medications[0].name").value("혈압약"))
                .andExpect(jsonPath("$.data.voiceText").value("아침 약 드실 시간이에요. 약이 1개 있어요."));
        assertThat(jdbcTemplate.queryForObject("SELECT answered_at FROM dose_sessions WHERE id = ?", String.class, sessionId))
                .isEqualTo(answeredAt);

        // 진행 중 회차는 앱을 다시 열어도 current로 보임
        getAs(token, currentUrl(f.seniorId()))
                .andExpect(jsonPath("$.data.current.sessionId").value(sessionId))
                .andExpect(jsonPath("$.data.current.status").value("IN_PROGRESS"));
        getAs(token, "/api/v1/seniors/" + f.seniorId() + "/schedules")
                .andExpect(jsonPath("$.data.sessions[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.sessions[0].answeredAt").isNotEmpty());
    }

    @Test
    void noAnswerRetriesTwiceThenMarksMissed() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        registerMedication(f.accountId(), f.seniorId(), "허리약", D, D, "MORNING");
        passAllTimes(token, f.seniorId());
        long sessionId = currentSessionId(token, f.seniorId());

        noAnswer(token, sessionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.callAttempts").value(1))
                .andExpect(jsonPath("$.data.nextCallAt").isNotEmpty());
        // 재전화 시각 전에는 수신화면을 띄우지 않음
        getAs(token, currentUrl(f.seniorId()))
                .andExpect(jsonPath("$.data.current").doesNotExist())
                .andExpect(jsonPath("$.data.next.timeSlot").value("MORNING"));

        noAnswer(token, sessionId).andExpect(jsonPath("$.data.callAttempts").value(2));
        noAnswer(token, sessionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("MISSED"))
                .andExpect(jsonPath("$.data.callAttempts").value(3))
                .andExpect(jsonPath("$.data.nextCallAt").doesNotExist());
        assertThat(count("SELECT COUNT(*) FROM medication_logs ml JOIN medication m ON ml.medication_id = m.id "
                + "WHERE m.senior_profile_id = ? AND ml.target_date = ? AND ml.status = 'MISSED'", f.seniorId(), D)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT finished_at FROM dose_sessions WHERE id = ?", String.class, sessionId)).isNotNull();

        noAnswer(token, sessionId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SENIOR_004"));
        answer(token, sessionId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SENIOR_004"));
        // 미복용 약은 하루 연장되므로 다음 전화는 내일 아침
        getAs(token, currentUrl(f.seniorId()))
                .andExpect(jsonPath("$.data.current").doesNotExist())
                .andExpect(jsonPath("$.data.next.timeSlot").value("MORNING"))
                .andExpect(jsonPath("$.data.next.scheduledAt").value(containsString(TODAY.plusDays(1).toString())));
        getAs(token, "/api/v1/seniors/" + f.seniorId() + "/schedules")
                .andExpect(jsonPath("$.data.sessions[0].status").value("MISSED"))
                .andExpect(jsonPath("$.data.sessions[0].medications[0].status").value("MISSED"));
    }

    @Test
    void noAnswerAfterAnswerIsRejected() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        registerMedication(f.accountId(), f.seniorId(), "허리약", D, D, "MORNING");
        passAllTimes(token, f.seniorId());
        long sessionId = currentSessionId(token, f.seniorId());

        answer(token, sessionId).andExpect(status().isOk());
        noAnswer(token, sessionId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SENIOR_005"));
    }

    @Test
    void rejectsInaccessibleOrUnknownSessions() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        registerMedication(f.accountId(), f.seniorId(), "허리약", D, D, "MORNING");
        passAllTimes(token, f.seniorId());
        long sessionId = currentSessionId(token, f.seniorId());

        Family other = newFamily();
        answer(bearer(other.accountId()), sessionId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SENIOR_003"));
        noAnswer(bearer(other.accountId()), sessionId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SENIOR_003"));
        answer(token, 999_999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SENIOR_003"));
        getAs(bearer(other.accountId()), currentUrl(f.seniorId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_002"));
        getAs(token, currentUrl(f.guardianId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEDICATION_003"));
        mockMvc.perform(post("/api/v1/dose-sessions/" + sessionId + "/answer")).andExpect(status().isUnauthorized());

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM dose_sessions WHERE id = ?", String.class, sessionId))
                .isEqualTo("SCHEDULED");
    }

    // 모든 회차 시각을 자정 직후로 보내 "이미 울릴 시각"으로 만듦
    private void passAllTimes(String token, long seniorId) throws Exception {
        putDoseTimes(token, seniorId, List.of(slot("MORNING", "00:00"), slot("LUNCH", "00:01"),
                slot("DINNER", "00:02"), slot("BEFORE_SLEEP", "00:03"))).andExpect(status().isOk());
    }

    private long currentSessionId(String token, long seniorId) throws Exception {
        return jsonMapper.readTree(getAs(token, currentUrl(seniorId)).andReturn().getResponse().getContentAsString())
                .path("data").path("current").path("sessionId").asLong();
    }

    private ResultActions answer(String token, long sessionId) throws Exception {
        return postJson(token, "/api/v1/dose-sessions/" + sessionId + "/answer", "");
    }

    private ResultActions noAnswer(String token, long sessionId) throws Exception {
        return postJson(token, "/api/v1/dose-sessions/" + sessionId + "/no-answer", "");
    }

    private ResultActions putDoseTimes(String token, long seniorId, List<Map<String, String>> items) throws Exception {
        return putJson(token, "/api/v1/seniors/" + seniorId + "/dose-times", Map.of("doseTimes", items));
    }

    private static Map<String, String> slot(String timeSlot, String doseTime) {
        return Map.of("timeSlot", timeSlot, "doseTime", doseTime);
    }

    private static String currentUrl(long seniorId) {
        return "/api/v1/seniors/" + seniorId + "/dose-sessions/current";
    }
}
