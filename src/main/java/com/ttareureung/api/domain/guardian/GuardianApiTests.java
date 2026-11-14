package com.ttareureung.api.domain.guardian;

import com.ttareureung.api.support.ApiTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GuardianApiTests extends ApiTestSupport {

    @Test
    void firstLinkBecomesPrimary() throws Exception {
        Family f = newFamily();

        assertThat(primaryGuardianIds(f.seniorId())).containsExactly(f.guardianId());
    }

    @Test
    void primaryMovesToRemainingGuardianWhenPrimaryProfileIsDeleted() throws Exception {
        Family f = newFamily();
        long guardian2 = createProfile(f.accountId(), "아무개", "GUARDIAN", null);
        linkProfiles(f.accountId(), guardian2, f.seniorId());

        deleteAs(bearer(f.accountId()), "/api/v1/profiles/" + f.guardianId()).andExpect(status().isOk());

        assertThat(primaryGuardianIds(f.seniorId())).containsExactly(guardian2);
    }

    @Test
    void primaryMovesToRemainingGuardianWhenPrimaryLinkIsRemoved() throws Exception {
        Family f = newFamily();
        long guardian2 = createProfile(f.accountId(), "아무개", "GUARDIAN", null);
        linkProfiles(f.accountId(), guardian2, f.seniorId());
        long linkId = jdbcTemplate.queryForObject(
                "SELECT id FROM guardian_seniors WHERE guardian_profile_id = ? AND senior_profile_id = ?",
                Long.class, f.guardianId(), f.seniorId());

        deleteAs(bearer(f.accountId()), "/api/v1/guardian-seniors/" + linkId).andExpect(status().isOk());

        assertThat(primaryGuardianIds(f.seniorId())).containsExactly(guardian2);
    }

    @Test
    void guardianCannotBeDeletedIfLastGuardianOfSenior() throws Exception {
        Family f = newFamily();

        deleteAs(bearer(f.accountId()), "/api/v1/profiles/" + f.guardianId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROFILE_005"));
    }

    private List<Long> primaryGuardianIds(long seniorId) {
        return jdbcTemplate.queryForList(
                "SELECT guardian_profile_id FROM guardian_seniors WHERE senior_profile_id = ? AND is_primary = true",
                Long.class);
    }
}
