package com.ttareureung.api.domain.guardian;

import tools.jackson.databind.JsonNode;
import com.ttareureung.api.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GuardianInvitationLinkApiTests extends ApiTestSupport {

    @Test
    void inviteReturnsLinkAndReusesPendingInvitation() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());

        JsonNode first = data(invite(token, f.seniorId(), "010-1234-5678", "010-2222-3333")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invited_count").value(2))
                .andExpect(jsonPath("$.data.invitations.length()").value(2))
                .andExpect(jsonPath("$.data.invitations[0].phone_number").value("01012345678"))
                .andExpect(jsonPath("$.data.invitations[0].invite_url").value(startsWith("http://localhost:5173/invite?token=")))
                .andExpect(jsonPath("$.data.invitations[0].expires_at").isNotEmpty())
                .andExpect(jsonPath("$.data.invitations[0].newly_created").value(true)));
        String tokenA = first.path("invitations").get(0).path("token").asText();
        assertThat(tokenA).hasSize(32);
        assertThat(first.path("invitations").get(0).path("invite_url").asText()).endsWith(tokenA);

        // 대기 중인 번호는 새 초대 없이 같은 링크, 새 번호만 생성
        invite(token, f.seniorId(), "01012345678", "010-5555-6666")
                .andExpect(jsonPath("$.data.invited_count").value(1))
                .andExpect(jsonPath("$.data.invitations.length()").value(2))
                .andExpect(jsonPath("$.data.invitations[0].token").value(tokenA))
                .andExpect(jsonPath("$.data.invitations[0].newly_created").value(false))
                .andExpect(jsonPath("$.data.invitations[1].newly_created").value(true));
        assertThat(count("SELECT COUNT(*) FROM guardian_invitations WHERE senior_profile_id = ?", f.seniorId())).isEqualTo(3);

        // 이미 연결된 보호자 번호는 목록에서도 제외
        long guardian2 = createProfile(f.accountId(), "아무개", "GUARDIAN", "010-9999-0000");
        linkProfiles(f.accountId(), guardian2, f.seniorId());
        invite(token, f.seniorId(), "01099990000")
                .andExpect(jsonPath("$.data.invited_count").value(0))
                .andExpect(jsonPath("$.data.invitations.length()").value(0));
    }

    @Test
    void acceptReturnsSeniorInfoAndLinkIdWithoutTakingPrimary() throws Exception {
        Family f = newFamily();
        String inviteToken = inviteToken(f, "010-1234-5678");
        long accountB = newAccount();
        long guardianB = createProfile(accountB, "초대받은 보호자", "GUARDIAN", null);

        JsonNode accepted = data(accept(bearer(accountB), inviteToken, guardianB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.senior_profile_id").value(f.seniorId()))
                .andExpect(jsonPath("$.data.senior_name").value("김순자"))
                .andExpect(jsonPath("$.data.link_id").isNumber()));
        long linkId = accepted.path("link_id").asLong();
        var link = jdbcTemplate.queryForMap("SELECT * FROM guardian_seniors WHERE id = ?", linkId);
        assertThat(((Number) link.get("guardian_profile_id")).longValue()).isEqualTo(guardianB);
        assertThat(link.get("is_primary")).isIn(false, 0, (byte) 0);

        // 연결 없는 시니어를 수락하면 대표 보호자
        long seniorNoLink = createProfile(f.accountId(), "박종옥", "SENIOR", null);
        String second = jsonMapper.readTree(invite(bearer(f.accountId()), seniorNoLink, "010-7777-8888")
                .andReturn().getResponse().getContentAsString()).path("data").path("invitations").get(0).path("token").asText();
        long linkId2 = data(accept(bearer(accountB), second, guardianB).andExpect(status().isOk())).path("link_id").asLong();
        assertThat(jdbcTemplate.queryForMap("SELECT is_primary FROM guardian_seniors WHERE id = ?", linkId2).get("is_primary"))
                .isIn(true, 1, (byte) 1);
    }

    @Test
    void seniorAccountCanUnlinkExternalGuardianAndRevokeAccess() throws Exception {
        Family f = newFamily();
        String tokenA = bearer(f.accountId());
        String inviteToken = inviteToken(f, "010-1234-5678");
        long accountB = newAccount();
        long guardianB = createProfile(accountB, "초대받은 보호자", "GUARDIAN", null);
        String tokenB = bearer(accountB);
        long linkId = data(accept(tokenB, inviteToken, guardianB).andExpect(status().isOk())).path("link_id").asLong();
        getAs(tokenB, "/api/v1/seniors/" + f.seniorId() + "/dose-times").andExpect(status().isOk());

        // 제3자는 불가
        deleteAs(bearer(newAccount()), "/api/v1/guardian-seniors/" + linkId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_005"));

        // 시니어 쪽 계정에서 해제 → 외부 보호자 접근 상실
        deleteAs(tokenA, "/api/v1/guardian-seniors/" + linkId).andExpect(status().isOk());
        assertThat(count("SELECT COUNT(*) FROM guardian_seniors WHERE id = ?", linkId)).isZero();
        getAs(tokenB, "/api/v1/seniors/" + f.seniorId() + "/dose-times")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_002"));
        // 초대는 이미 사용됐으므로 재수락 불가
        accept(tokenB, inviteToken, guardianB).andExpect(status().isNotFound());

        // 외부 보호자 쪽 계정에서도 자기 연결은 해제 가능
        long linkId2 = data(accept(tokenB, inviteToken(f, "010-3333-4444"), guardianB).andExpect(status().isOk())).path("link_id").asLong();
        deleteAs(tokenB, "/api/v1/guardian-seniors/" + linkId2).andExpect(status().isOk());
        assertThat(count("SELECT COUNT(*) FROM guardian_seniors WHERE id = ?", linkId2)).isZero();
    }

    @Test
    void unlinkingPrimaryExternalGuardianFromSeniorSideHandsOverPrimary() throws Exception {
        Family f = newFamily();
        String tokenA = bearer(f.accountId());
        long accountB = newAccount();
        long guardianB = createProfile(accountB, "초대받은 보호자", "GUARDIAN", null);
        long linkId = data(accept(bearer(accountB), inviteToken(f, "010-1234-5678"), guardianB)
                .andExpect(status().isOk())).path("link_id").asLong();

        patchJson(tokenA, "/api/v1/guardians/primary", Map.of("senior_profile_id", f.seniorId(), "guardian_id", guardianB))
                .andExpect(status().isOk());
        assertThat(primaryGuardianIds(f.seniorId())).containsExactly(guardianB);

        deleteAs(tokenA, "/api/v1/guardian-seniors/" + linkId).andExpect(status().isOk());
        assertThat(primaryGuardianIds(f.seniorId())).containsExactly(f.guardianId());
    }

    private String inviteToken(Family f, String phone) throws Exception {
        return data(invite(bearer(f.accountId()), f.seniorId(), phone).andExpect(status().isOk()))
                .path("invitations").get(0).path("token").asText();
    }

    private ResultActions invite(String token, long seniorId, String... phones) throws Exception {
        return postJson(token, "/api/v1/guardians/invitation",
                Map.of("senior_profile_id", seniorId, "phone_numbers", List.of(phones)));
    }

    private ResultActions accept(String token, String inviteToken, long guardianProfileId) throws Exception {
        return postJson(token, "/api/v1/guardians/invitation/accept",
                Map.of("token", inviteToken, "guardian_profile_id", guardianProfileId));
    }

    private JsonNode data(ResultActions actions) throws Exception {
        return jsonMapper.readTree(actions.andReturn().getResponse().getContentAsString()).path("data");
    }

    private List<Long> primaryGuardianIds(long seniorId) {
        return jdbcTemplate.queryForList(
                "SELECT guardian_profile_id FROM guardian_seniors WHERE senior_profile_id = ? AND is_primary = TRUE",
                Long.class, seniorId);
    }
}
