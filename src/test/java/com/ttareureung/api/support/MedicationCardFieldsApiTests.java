package com.ttareureung.api.support;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

class MedicationCardFieldsApiTests extends ApiTestSupport {

    private static final String URL = "/api/v1/medications";
    private static final String D = "2026-11-04";
    private static final String PILL_URL = "https://example.com/pill.png";

    // Spring Boot 4 = Jackson 3 (tools.jackson) 기준으로 응답 JSON을 읽기 위한 매퍼
    private static final JsonMapper JACKSON3 = JsonMapper.builder().build();

    @Test
    void strengthIsStoredAndReturnedInMedicationList() throws Exception {
        Family f = newFamily();
        var item = medicationItem("아로르신정", D, D, "MORNING");
        item.put("strength", "25mg");
        long id = register(f, item);

        assertThat(jdbcTemplate.queryForObject("SELECT strength FROM medication WHERE id = ?", String.class, id))
                .isEqualTo("25mg");
        getAs(bearer(f.accountId()), URL + "?senior_id=" + f.seniorId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.medications[0].strength").value("25mg"))
                .andExpect(jsonPath("$.data.medications[0].dose_amount").value("1정"));
    }

    @Test
    void bulkRegisterStoresStrengthPerItem() throws Exception {
        Family f = newFamily();
        var a = medicationItem("약A", D, D, "MORNING");
        a.put("strength", "5mg");
        var b = medicationItem("약B", D, D, "LUNCH");
        b.put("strength", "500mg");

        postJson(bearer(f.accountId()), URL + "/bulk",
                Map.of("senior_profile_id", f.seniorId(), "medications", List.of(a, b)))
                .andExpect(status().isCreated());

        assertThat(jdbcTemplate.queryForList(
                "SELECT strength FROM medication WHERE senior_profile_id = ? ORDER BY id", String.class, f.seniorId()))
                .containsExactly("5mg", "500mg");
    }

    @Test
    void updateChangesKeepsAndClearsStrength() throws Exception {
        Family f = newFamily();
        var item = medicationItem("혈압약", D, D, "MORNING");
        item.put("strength", "5mg");
        long id = register(f, item);
        String token = bearer(f.accountId());

        putJson(token, URL + "/" + id, Map.of("strength", "10mg")).andExpect(status().isOk());
        assertThat(strengthOf(id)).isEqualTo("10mg");

        putJson(token, URL + "/" + id, Map.of("effect", "혈압 조절")).andExpect(status().isOk()); // 안 보내면 유지
        assertThat(strengthOf(id)).isEqualTo("10mg");

        putJson(token, URL + "/" + id, Map.of("strength", "")).andExpect(status().isOk());       // 빈 문자열이면 삭제
        assertThat(strengthOf(id)).isNull();
    }

    @Test
    void rejectsTooLongStrengthOnRegisterAndUpdate() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        long id = registerMedication(f.accountId(), f.seniorId(), "혈압약", D, D, "MORNING");

        var body = medicationItem("약", D, D, "MORNING");
        body.put("senior_profile_id", f.seniorId());
        body.put("strength", "가".repeat(21));
        postJson(token, URL, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
        putJson(token, URL + "/" + id, Map.of("strength", "가".repeat(21)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    void scheduleCardsCarryImageUrlStrengthAndDoseAmount() throws Exception {
        Family f = newFamily();
        var withCard = medicationItem("혈압약", D, D, "MORNING");
        withCard.put("strength", "5mg");
        withCard.put("image_url", PILL_URL);
        long a = register(f, withCard);
        long b = register(f, medicationItem("소화제", D, D, "MORNING"));   // 사진·함량 없음

        MvcResult result = getAs(bearer(f.accountId()), "/api/v1/seniors/" + f.seniorId() + "/schedules?date=" + D)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessions.length()").value(1))
                .andExpect(jsonPath("$.data.sessions[0].sessionId").isEmpty())   // 오늘이 아니면 회차 없음
                .andExpect(jsonPath("$.data.sessions[0].slotName").value("아침"))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andReturn();

        JsonNode card = scheduleMedication(result, a);
        assertThat(card.path("strength").asString()).isEqualTo("5mg");
        assertThat(card.path("doseAmount").asString()).isEqualTo("1정");
        assertThat(card.path("imageUrl").asString()).isEqualTo(PILL_URL);

        JsonNode plain = scheduleMedication(result, b);
        assertThat(plain.path("strength").isNull() || plain.path("strength").isMissingNode()).isTrue();
        assertThat(plain.path("imageUrl").isNull() || plain.path("imageUrl").isMissingNode()).isTrue();
    }

    @Test
    void timelineMedicinesCarryImageUrlAndStrengthToo() throws Exception {
        Family f = newFamily();
        var item = medicationItem("혈압약", D, D, "MORNING");
        item.put("strength", "5mg");
        item.put("image_url", PILL_URL);
        register(f, item);

        getAs(bearer(f.accountId()), scheduleUrl(f.seniorId(), D))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.time_slots[0].medicines[0].strength").value("5mg"))
                .andExpect(jsonPath("$.data.time_slots[0].medicines[0].dose_amount").value("1정"))
                .andExpect(jsonPath("$.data.time_slots[0].medicines[0].image_url").value(PILL_URL));
    }

    @Test
    void otherAccountCannotReadSchedule() throws Exception {
        Family f = newFamily();

        getAs(bearer(newAccount()), "/api/v1/seniors/" + f.seniorId() + "/schedules?date=" + D)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_002"));
    }

    private long register(Family f, Map<String, Object> item) throws Exception {
        item.put("senior_profile_id", f.seniorId());
        return idOf(postJson(bearer(f.accountId()), URL, item).andExpect(status().isCreated()).andReturn(),
                "medication_id");
    }

    private String strengthOf(long medicationId) {
        return jdbcTemplate.queryForObject("SELECT strength FROM medication WHERE id = ?", String.class, medicationId);
    }

    private JsonNode scheduleMedication(MvcResult result, long medicationId) throws Exception {
        JsonNode root = JACKSON3.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        for (JsonNode session : root.path("data").path("sessions")) {
            for (JsonNode medication : session.path("medications")) {
                if (medication.path("medicationId").asLong() == medicationId) {
                    return medication;
                }
            }
        }
        throw new AssertionError("일정에 없는 약: " + medicationId);
    }
}