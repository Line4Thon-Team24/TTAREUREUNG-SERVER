package com.ttareureung.api.domain.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.ttareureung.api.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfileLinkScheduleFieldsApiTests extends ApiTestSupport {

    private static final String D = LocalDate.now(ZoneId.of("Asia/Seoul")).toString();

    @Test
    void profileResponsesIncludeCreatedAtAndFavoriteSharedInAccount() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());

        getAs(token, "/api/v1/profiles/" + f.seniorId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isFavorite").value(false))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty());

        patchJson(token, "/api/v1/profiles/" + f.seniorId(), Map.of("isFavorite", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isFavorite").value(true));
        // 다른 필드 수정은 즐겨찾기 유지
        patchJson(token, "/api/v1/profiles/" + f.seniorId(), Map.of("name", "김순자2"))
                .andExpect(jsonPath("$.data.isFavorite").value(true));
        getAs(token, "/api/v1/profiles")
                .andExpect(jsonPath("$.data[1].profileId").value(f.seniorId()))
                .andExpect(jsonPath("$.data[1].isFavorite").value(true))
                .andExpect(jsonPath("$.data[0].isFavorite").value(false));
        assertThat(jdbcTemplate.queryForObject("SELECT is_favorite FROM profiles WHERE id = ?", Boolean.class, f.seniorId())).isTrue();

        patchJson(token, "/api/v1/profiles/" + f.seniorId(), Map.of("isFavorite", false))
                .andExpect(jsonPath("$.data.isFavorite").value(false));
        patchJson(bearer(newAccount()), "/api/v1/profiles/" + f.seniorId(), Map.of("isFavorite", true))
                .andExpect(status().isNotFound());
    }

    @Test
    void linkListShowsGuardianDetailsIncludingInvitedGuardianOfAnotherAccount() throws Exception {
        Family f = newFamily();
        String tokenA = bearer(f.accountId());
        long accountB = newAccount();
        long guardianB = createProfile(accountB, "외부 보호자", "GUARDIAN", "010-7777-8888");
        acceptInvitation(f, accountB, guardianB);

        JsonNode links = data(getAs(tokenA, "/api/v1/guardian-seniors").andExpect(status().isOk()));
        assertThat(links).hasSize(2);
        JsonNode external = links.get(1).path("guardian");
        assertThat(external.path("profileId").asLong()).isEqualTo(guardianB);
        assertThat(external.path("phoneNumber").asText()).isEqualTo("010-7777-8888");
        assertThat(external.has("birthDate")).isTrue();
        assertThat(external.has("gender")).isTrue();
        assertThat(links.get(0).path("senior").path("profileId").asLong()).isEqualTo(f.seniorId());
    }

    @Test
    void changesLinkedSeniorKeepingLinkIdAndHandingOverPrimary() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        long guardian2 = createProfile(f.accountId(), "김가족", "GUARDIAN", null);
        linkProfiles(f.accountId(), guardian2, f.seniorId());
        long senior2 = createProfile(f.accountId(), "박종옥", "SENIOR", null);
        long primaryLink = linkIdOf(f.guardianId(), f.seniorId());

        changeSenior(token, primaryLink, senior2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.linkId").value(primaryLink))
                .andExpect(jsonPath("$.data.senior.profileId").value(senior2))
                .andExpect(jsonPath("$.data.isPrimary").value(true)); // 새 시니어의 첫 연결

        // 원래 시니어의 대표는 남은 보호자에게
        assertThat(primaryGuardians(f.seniorId())).containsExactly(guardian2);
        assertThat(primaryGuardians(senior2)).containsExactly(f.guardianId());

        // 같은 시니어로 다시 요청하면 그대로
        changeSenior(token, primaryLink, senior2).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.linkId").value(primaryLink));

        // 이미 연결된 시니어로는 불가
        long guardian2Link = linkIdOf(guardian2, f.seniorId());
        linkProfiles(f.accountId(), guardian2, senior2);
        changeSenior(token, guardian2Link, senior2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROFILE_004"));
    }

    @Test
    void changeSeniorRejectsInaccessibleTargetsAndLinks() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        long link = linkIdOf(f.guardianId(), f.seniorId());
        Family other = newFamily();

        changeSenior(token, link, other.seniorId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_002"));
        changeSenior(token, link, f.guardianId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEDICATION_003"));
        changeSenior(bearer(other.accountId()), link, other.seniorId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_005"));
        patchJson(token, "/api/v1/guardian-seniors/" + link, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
        assertThat(count("SELECT COUNT(*) FROM guardian_seniors WHERE id = ? AND senior_profile_id = ?", link, f.seniorId()))
                .isEqualTo(1);
    }

    @Test
    void strengthIsStoredAndShownWithImageInSeniorSchedule() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        var body = medicationItem("노바스크", D, D, "MORNING");
        body.put("strength", "5mg");
        body.put("image_url", "https://example.com/pill.png");
        body.put("senior_profile_id", f.seniorId());
        long medicationId = idOf(postJson(token, "/api/v1/medications", body).andExpect(status().isCreated()).andReturn(),
                "medication_id");

        getAs(token, "/api/v1/medications?senior_id=" + f.seniorId())
                .andExpect(jsonPath("$.data.medications[0].strength").value("5mg"));
        getAs(token, "/api/v1/seniors/" + f.seniorId() + "/schedules")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessions[0].medications[0].strength").value("5mg"))
                .andExpect(jsonPath("$.data.sessions[0].medications[0].doseAmount").value("1정"))
                .andExpect(jsonPath("$.data.sessions[0].medications[0].imageUrl").value(startsWith("https://example.com/")));

        putJson(token, "/api/v1/medications/" + medicationId, Map.of("strength", "10mg")).andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT strength FROM medication WHERE id = ?", String.class, medicationId))
                .isEqualTo("10mg");
        putJson(token, "/api/v1/medications/" + medicationId, Map.of("strength", "")).andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT strength FROM medication WHERE id = ?", String.class, medicationId))
                .isNull();
        putJson(token, "/api/v1/medications/" + medicationId, Map.of("strength", "가".repeat(31)))
                .andExpect(status().isBadRequest());
    }

    private void acceptInvitation(Family f, long accountB, long guardianB) throws Exception {
        String inviteToken = data(postJson(bearer(f.accountId()), "/api/v1/guardians/invitation",
                Map.of("senior_profile_id", f.seniorId(), "phone_numbers", List.of("010-7777-8888"))))
                .path("invitations").get(0).path("token").asText();
        postJson(bearer(accountB), "/api/v1/guardians/invitation/accept",
                Map.of("token", inviteToken, "guardian_profile_id", guardianB)).andExpect(status().isOk());
    }

    private ResultActions changeSenior(String token, long linkId, long seniorId) throws Exception {
        return patchJson(token, "/api/v1/guardian-seniors/" + linkId, Map.of("seniorProfileId", seniorId));
    }

    private long linkIdOf(long guardianId, long seniorId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM guardian_seniors WHERE guardian_profile_id = ? AND senior_profile_id = ?",
                Long.class, guardianId, seniorId);
    }

    private List<Long> primaryGuardians(long seniorId) {
        return jdbcTemplate.queryForList(
                "SELECT guardian_profile_id FROM guardian_seniors WHERE senior_profile_id = ? AND is_primary = TRUE",
                Long.class, seniorId);
    }

    private JsonNode data(ResultActions actions) throws Exception {
        return jsonMapper.readTree(actions.andReturn().getResponse().getContentAsString()).path("data");
    }
}
