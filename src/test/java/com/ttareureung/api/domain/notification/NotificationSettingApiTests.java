package com.ttareureung.api.domain.notification;

import com.ttareureung.api.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationSettingApiTests extends ApiTestSupport {

    private static final String URL = "/api/notification-settings";

    @Test
    void returnsDefaultsWithoutCreatingRow() throws Exception {
        Family f = newFamily();

        getAs(bearer(f.accountId()), URL + "?profile_id=" + f.guardianId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile_id").value(f.guardianId()))
                .andExpect(jsonPath("$.data.notify_on_complete").value(true))
                .andExpect(jsonPath("$.data.notify_on_miss").value(true))
                .andExpect(jsonPath("$.data.notify_on_delay").value(true))
                .andExpect(jsonPath("$.data.remind_before_minutes").value(10));
        assertThat(count("SELECT COUNT(*) FROM notification_setting WHERE profile_id = ?", f.guardianId())).isZero();
    }

    @Test
    void updatesOnlySentFieldsAndKeepsSingleRow() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());

        patchJson(token, URL, Map.of("profile_id", f.guardianId(), "remind_before_minutes", 30, "notify_on_complete", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remind_before_minutes").value(30))
                .andExpect(jsonPath("$.data.notify_on_complete").value(false))
                .andExpect(jsonPath("$.data.notify_on_miss").value(true));

        patchJson(token, URL, Map.of("profile_id", f.guardianId(), "notify_on_miss", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remind_before_minutes").value(30))    // 이전 값 유지
                .andExpect(jsonPath("$.data.notify_on_complete").value(false))
                .andExpect(jsonPath("$.data.notify_on_miss").value(false));

        getAs(token, URL + "?profile_id=" + f.guardianId())
                .andExpect(jsonPath("$.data.remind_before_minutes").value(30))
                .andExpect(jsonPath("$.data.notify_on_delay").value(true));
        assertThat(count("SELECT COUNT(*) FROM notification_setting WHERE profile_id = ?", f.guardianId())).isEqualTo(1);
    }

    @ParameterizedTest(name = "invalid setting body {index}")
    @ValueSource(strings = {
            "{}",
            "{\"remind_before_minutes\":10}",
            "{\"profile_id\":1,\"remind_before_minutes\":-1}",
            "{\"profile_id\":1,\"remind_before_minutes\":181}",
            "{\"profile_id\":1,\"notify_on_complete\":\"yes\"}"
    })
    void rejectsInvalidBody(String body) throws Exception {
        patchJson(bearer(newAccount()), URL, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    void otherAccountsProfileLooksLikeNotFound() throws Exception {
        Family f = newFamily();
        String stranger = bearer(newAccount());

        getAs(stranger, URL + "?profile_id=" + f.guardianId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_002"));
        patchJson(stranger, URL, Map.of("profile_id", f.guardianId(), "notify_on_complete", false))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_002"));
        assertThat(count("SELECT COUNT(*) FROM notification_setting WHERE profile_id = ?", f.guardianId())).isZero();
    }

    @Test
    void inactiveAccountCannotChangeSettingsAndTokenIsRequired() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        jdbcTemplate.update("UPDATE accounts SET status = 'INACTIVE' WHERE id = ?", f.accountId());

        patchJson(token, URL, Map.of("profile_id", f.guardianId(), "notify_on_complete", false))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_005"));
        mockMvc.perform(get(URL + "?profile_id=1")).andExpect(status().isUnauthorized());
    }

    @Test
    void deletingProfileAlsoRemovesItsSetting() throws Exception {
        Family f = newFamily();
        long guardian2 = createProfile(f.accountId(), "아무개", "GUARDIAN", null);
        String token = bearer(f.accountId());
        patchJson(token, URL, Map.of("profile_id", guardian2, "remind_before_minutes", 30)).andExpect(status().isOk());
        try {
            deleteAs(token, "/api/v1/profiles/" + guardian2).andExpect(status().isOk());
            assertThat(count("SELECT COUNT(*) FROM notification_setting WHERE profile_id = ?", guardian2)).isZero();
        } finally {
            jdbcTemplate.update("DELETE FROM notification_setting WHERE profile_id = ?", guardian2);
        }
    }
}
