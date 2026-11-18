package com.ttareureung.api.support;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

class MedicationScheduleEditApiTests extends ApiTestSupport {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String URL = "/api/v1/medications";
    private static final String D = "2026-11-04";

    // ── 간격 · 복용 시점 · 새 필드 ──
    @Test
    void everyOtherDayCreatesLogsOnlyOnMatchingDays() throws Exception {
        Family f = newFamily();
        var body = medicationItem("혈압약", "2026-11-01", "2026-11-07", "MORNING", "BEFORE_SLEEP");
        body.put("interval_days", 2);

        long id = register(f, body);

        assertThat(jdbcTemplate.queryForList("""
                SELECT DISTINCT CAST(target_date AS CHAR) FROM medication_logs
                WHERE medication_id = ? ORDER BY 1""", String.class, id))
                .containsExactly("2026-11-01", "2026-11-03", "2026-11-05", "2026-11-07");
        assertThat(count("SELECT COUNT(*) FROM medication_logs WHERE medication_id = ?", id)).isEqualTo(8);
    }

    @Test
    void composesFrequencyWhenOmittedAndKeepsExplicitOne() throws Exception {
        Family f = newFamily();

        var composed = medicationItem("약A", D, D, "MORNING");
        composed.remove("frequency");
        composed.put("intake_timing", "AFTER_MEAL_30M");
        long a = register(f, composed);

        var everyOtherDay = medicationItem("약B", D, "2026-11-09", "BEFORE_SLEEP");
        everyOtherDay.remove("frequency");
        everyOtherDay.put("interval_days", 2);
        everyOtherDay.put("intake_timing", "BEFORE_BED");
        long b = register(f, everyOtherDay);

        var explicit = medicationItem("약C", D, D, "LUNCH");   // 헬퍼가 frequency를 직접 보냄
        explicit.put("intake_timing", "RIGHT_AFTER_MEAL");
        long c = register(f, explicit);

        assertThat(frequencyOf(a)).isEqualTo("매일 / 식사 30분 후");
        assertThat(frequencyOf(b)).isEqualTo("이틀에 한 번 / 취침 전");
        assertThat(frequencyOf(c)).isEqualTo("하루 2회, 식후 30분");
    }

    @Test
    void storesNewFieldsAndListReturnsThemWithDefaults() throws Exception {
        Family f = newFamily();
        var ai = medicationItem("아미노부프로펜", D, D, "MORNING");
        ai.put("category", "진통소염제");
        ai.put("prescription_file_name", "처방전_20261105.jpg");
        ai.put("registration_type", "AI");
        ai.put("interval_days", 7);
        ai.put("intake_timing", "BEFORE_MEAL_30M");
        register(f, ai);
        register(f, medicationItem("직접 입력 약", D, D, "LUNCH"));

        var aiItem = listItem(f, "아미노부프로펜");
        assertThat(aiItem.path("category").asText()).isEqualTo("진통소염제");
        assertThat(aiItem.path("prescription_file_name").asText()).isEqualTo("처방전_20261105.jpg");
        assertThat(aiItem.path("registration_type").asText()).isEqualTo("AI");
        assertThat(aiItem.path("interval_days").asInt()).isEqualTo(7);
        assertThat(aiItem.path("intake_timing").asText()).isEqualTo("BEFORE_MEAL_30M");

        var manualItem = listItem(f, "직접 입력 약");
        assertThat(manualItem.path("registration_type").asText()).isEqualTo("MANUAL");
        assertThat(manualItem.path("interval_days").asInt()).isEqualTo(1);
        assertThat(manualItem.path("intake_timing").isNull() || manualItem.path("intake_timing").isMissingNode()).isTrue();
    }

    @Test
    void rejectsInvalidIntervalTimingAndRegistrationType() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());

        for (Map.Entry<String, Object> bad : List.<Map.Entry<String, Object>>of(
                Map.entry("interval_days", 0), Map.entry("interval_days", 31),
                Map.entry("intake_timing", "AFTER_DINNER"), Map.entry("registration_type", "VOICE"))) {
            var body = medicationItem("약", D, D, "MORNING");
            body.put("senior_profile_id", f.seniorId());
            body.put(bad.getKey(), bad.getValue());
            postJson(token, URL, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON_400"));
        }
        assertThat(count("SELECT COUNT(*) FROM medication WHERE senior_profile_id = ?", f.seniorId())).isZero();
    }

    // ── PUT: 정보 수정 ──
    @Test
    void updatesDrugInfoAndRecomposesFrequencyWhenTimingChanges() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        var item = medicationItem("아미노부프로펜", D, "2026-11-05", "MORNING");
        item.remove("frequency");
        item.put("intake_timing", "AFTER_MEAL_30M");
        long id = register(f, item);
        long logsBefore = count("SELECT COUNT(*) FROM medication_logs WHERE medication_id = ?", id);
        assertThat(frequencyOf(id)).isEqualTo("매일 / 식사 30분 후");

        putJson(token, URL + "/" + id, Map.of("drug_name", "이부프로펜", "category", "해열진통제",
                "registration_type", "AI", "intake_timing", "BEFORE_BED"))
                .andExpect(status().isOk());

        var row = jdbcTemplate.queryForMap("SELECT * FROM medication WHERE id = ?", id);
        assertThat(row.get("drug_name")).isEqualTo("이부프로펜");
        assertThat(row.get("category")).isEqualTo("해열진통제");
        assertThat(row.get("registration_type")).isEqualTo("AI");
        assertThat(row.get("intake_timing")).isEqualTo("BEFORE_BED");
        assertThat(row.get("frequency")).isEqualTo("매일 / 취침 전");
        // 일정 항목이 그대로면 복용 기록은 건드리지 않는다
        assertThat(count("SELECT COUNT(*) FROM medication_logs WHERE medication_id = ?", id)).isEqualTo(logsBefore);

        // 직접 보낸 frequency는 그대로 저장, 종류는 빈 문자열로 삭제
        putJson(token, URL + "/" + id, Map.of("category", "", "frequency", "아침저녁으로 한 알씩"))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT category FROM medication WHERE id = ?", String.class, id)).isNull();
        assertThat(frequencyOf(id)).isEqualTo("아침저녁으로 한 알씩");

        putJson(token, URL + "/" + id, Map.of("drug_name", "  "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    // ── PUT: 일정 수정과 복용 기록 동기화 ──
    @Test
    void scheduleChangeKeepsPastAndConfirmedLogsAndSyncsPendingOnes() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        LocalDate today = LocalDate.now(KST);
        long id = registerMedication(f.accountId(), f.seniorId(), "혈압약",
                today.minusDays(2).toString(), today.plusDays(4).toString(), "MORNING");
        long yesterdayLog = logId(id, "MORNING", today.minusDays(1).toString());
        long tomorrowLog = logId(id, "MORNING", today.plusDays(1).toString());
        takeLog(token, yesterdayLog);   // 지난 기록 확정
        takeLog(token, tomorrowLog);    // 미래 기록 확정

        putJson(token, URL + "/" + id, Map.of("end_date", today.plusDays(2).toString(),
                "meal_time_slots", List.of("MORNING", "BEFORE_SLEEP")))
                .andExpect(status().isOk());

        // 지난 기록은 그대로: 아침 2건, 복용 완료 유지, 새 시간대는 생성되지 않음
        assertThat(count("SELECT COUNT(*) FROM medication_logs WHERE medication_id = ? AND target_date < ?", id, today))
                .isEqualTo(2);
        assertThat(count("""
                SELECT COUNT(*) FROM medication_logs
                WHERE medication_id = ? AND target_date < ? AND meal_time_slot = 'BEFORE_SLEEP'""", id, today)).isZero();
        assertThat(statusOf(yesterdayLog)).isEqualTo("TAKEN");
        // 확정된 미래 기록은 보존
        assertThat(statusOf(tomorrowLog)).isEqualTo("TAKEN");
        // 새 종료일 이후의 PENDING은 삭제
        assertThat(count("SELECT COUNT(*) FROM medication_logs WHERE medication_id = ? AND target_date > ?",
                id, today.plusDays(2))).isZero();
        // 새 시간대는 오늘~새 종료일까지 3건 생성, 아침은 지난 2건 + 오늘 이후 3건
        assertThat(count("SELECT COUNT(*) FROM medication_logs WHERE medication_id = ? AND meal_time_slot = 'BEFORE_SLEEP'", id))
                .isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM medication_logs WHERE medication_id = ? AND meal_time_slot = 'MORNING'", id))
                .isEqualTo(5);
    }

    @Test
    void intervalChangeRemovesOnlyUnmatchedPendingLogsAndRecomposesFrequency() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        LocalDate today = LocalDate.now(KST);
        long id = registerMedication(f.accountId(), f.seniorId(), "혈압약",
                today.toString(), today.plusDays(6).toString(), "MORNING");
        takeLog(token, logId(id, "MORNING", today.plusDays(1).toString())); // 새 간격에 안 맞지만 확정된 기록

        putJson(token, URL + "/" + id, Map.of("interval_days", 3)).andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForList("""
                SELECT CAST(target_date AS CHAR) FROM medication_logs
                WHERE medication_id = ? ORDER BY target_date""", String.class, id))
                .containsExactly(today.toString(), today.plusDays(1).toString(),
                        today.plusDays(3).toString(), today.plusDays(6).toString());
        assertThat(frequencyOf(id)).isEqualTo("3일에 한 번");
    }

    @Test
    void rejectsInvalidScheduleEditsAndOtherAccounts() throws Exception {
        Family f = newFamily();
        Family other = newFamily();
        String token = bearer(f.accountId());
        long id = registerMedication(f.accountId(), f.seniorId(), "혈압약", "2026-11-04", "2026-11-10", "MORNING");
        String url = URL + "/" + id;

        putJson(token, url, Map.of("end_date", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEDICATION_004"));
        putJson(token, url, Map.of("start_date", "2026-11-01", "end_date", "2027-11-05"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEDICATION_005"));
        putJson(token, url, Map.of("meal_time_slots", List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
        putJson(token, url, Map.of("interval_days", 0)).andExpect(status().isBadRequest());
        putJson(bearer(other.accountId()), url, Map.of("end_date", "2026-11-20"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDICATION_001"));

        assertThat(jdbcTemplate.queryForObject("SELECT CAST(end_date AS CHAR) FROM medication WHERE id = ?", String.class, id))
                .isEqualTo("2026-11-10");
        assertThat(count("SELECT COUNT(*) FROM medication_logs WHERE medication_id = ?", id)).isEqualTo(7);
    }

    // ── 타임라인 ──
    @Test
    void timelineShowsBedtimeSlotLastWithIntakeTiming() throws Exception {
        Family f = newFamily();
        var item = medicationItem("수면 보조제", D, D, "BEFORE_SLEEP", "MORNING");
        item.put("intake_timing", "BEFORE_BED");
        register(f, item);

        getAs(bearer(f.accountId()), scheduleUrl(f.seniorId(), D))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.time_slots[0].slot").value("MORNING"))
                .andExpect(jsonPath("$.data.time_slots[0].slot_name").value("아침"))
                .andExpect(jsonPath("$.data.time_slots[1].slot").value("BEFORE_SLEEP"))
                .andExpect(jsonPath("$.data.time_slots[1].slot_name").value("자기 전"))
                .andExpect(jsonPath("$.data.time_slots[1].medicines[0].intake_timing").value("BEFORE_BED"));
    }

    private long register(Family f, Map<String, Object> item) throws Exception {
        item.put("senior_profile_id", f.seniorId());
        return idOf(postJson(bearer(f.accountId()), URL, item).andExpect(status().isCreated()).andReturn(), "medication_id");
    }

    private void takeLog(String token, long logId) throws Exception {
        patchJson(token, URL + "/logs/" + logId, Map.of("status", "TAKEN")).andExpect(status().isOk());
    }

    private String frequencyOf(long medicationId) {
        return jdbcTemplate.queryForObject("SELECT frequency FROM medication WHERE id = ?", String.class, medicationId);
    }

    private String statusOf(long logId) {
        return jdbcTemplate.queryForObject("SELECT status FROM medication_logs WHERE id = ?", String.class, logId);
    }

    private tools.jackson.databind.JsonNode listItem(Family f, String drugName) throws Exception {
        MvcResult result = getAs(bearer(f.accountId()), URL + "?senior_id=" + f.seniorId())
                .andExpect(status().isOk()).andReturn();

        for (var node : jsonMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("medications")) {
            if (drugName.equals(node.path("drug_name").asText())) {
                return node;
            }
        }
        throw new AssertionError("목록에 없는 약: " + drugName);
    }
}
