package com.ttareureung.api.domain.profile;

import com.ttareureung.api.domain.user.entity.Account;
import com.ttareureung.api.domain.user.repository.AccountRepository;
import com.ttareureung.api.global.security.JwtTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "jwt.secret=auth-test-secret-key-at-least-32-bytes",
        "jwt.cookie.secure=false"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class ProfileApiTests {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final List<Long> accountIds = new ArrayList<>();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @AfterEach
    void cleanUpCreatedAccounts() {
        for (Long id : accountIds) {
            jdbcTemplate.update("DELETE FROM profiles WHERE account_id = ?", id);
            jdbcTemplate.update("DELETE FROM accounts WHERE id = ?", id);
        }
    }

    @Test
    void createsSeniorProfileWithAllFields() throws Exception {
        long accountId = newAccount();
        create(accountId, profile("보호자", "GUARDIAN")).andExpect(status().isCreated());
        var request = profile("  김순자  ", "SENIOR");
        request.put("birthDate", "1950-03-15");
        request.put("gender", "FEMALE");
        request.put("phoneNumber", "010-1234-5678");

        MvcResult result = create(accountId, request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("김순자"))
                .andExpect(jsonPath("$.data.birthDate").value("1950-03-15"))
                .andExpect(jsonPath("$.data.gender").value("FEMALE"))
                .andExpect(jsonPath("$.data.phoneNumber").value("010-1234-5678"))
                .andExpect(jsonPath("$.data.role").value("SENIOR"))
                .andReturn();

        long profileId = jsonMapper.readTree(result.getResponse().getContentAsString()).path("data").path("profileId").asLong();
        var row = jdbcTemplate.queryForMap("SELECT * FROM profiles WHERE id = ?", profileId);
        assertThat(((Number) row.get("account_id")).longValue()).isEqualTo(accountId);
        assertThat(row.get("role")).isEqualTo("SENIOR");
        assertThat(row.get("created_at")).isNotNull();
        assertThat(row.get("updated_at")).isNotNull();
    }

    @Test
    void createsGuardianProfileWithOnlyRequiredFields() throws Exception {
        long accountId = newAccount();
        var request = profile("이희창", "GUARDIAN");
        request.put("phoneNumber", " ");

        create(accountId, request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("GUARDIAN"))
                .andExpect(jsonPath("$.data.birthDate").isEmpty())
                .andExpect(jsonPath("$.data.gender").isEmpty())
                .andExpect(jsonPath("$.data.phoneNumber").isEmpty());
    }

    @Test
    void listReturnsOnlyMyProfilesInCreatedOrder() throws Exception {
        long mine = newAccount();
        long other = newAccount();
        create(mine, profile("보호자", "GUARDIAN")).andExpect(status().isCreated());
        create(other, profile("다른 계정", "GUARDIAN")).andExpect(status().isCreated());
        create(mine, profile("시니어", "SENIOR")).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/profiles").header(HttpHeaders.AUTHORIZATION, bearer(mine)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("보호자"))
                .andExpect(jsonPath("$.data[1].name").value("시니어"));
    }

    @Test
    void listIsEmptyRightAfterSignup() throws Exception {
        mockMvc.perform(get("/api/v1/profiles").header(HttpHeaders.AUTHORIZATION, bearer(newAccount())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void seniorRequiresGuardianProfileFirst() throws Exception {
        long accountId = newAccount();

        create(accountId, profile("시니어", "SENIOR"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROFILE_001"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM profiles WHERE account_id = ?", Long.class, accountId))
                .isZero();

        create(accountId, profile("보호자", "GUARDIAN")).andExpect(status().isCreated());
        create(accountId, profile("시니어", "SENIOR")).andExpect(status().isCreated());
    }

    @Test
    void guardianInAnotherAccountDoesNotCount() throws Exception {
        long other = newAccount();
        create(other, profile("다른 계정 보호자", "GUARDIAN")).andExpect(status().isCreated());

        create(newAccount(), profile("시니어", "SENIOR"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROFILE_001"));
    }

    @Test
    void requiresAccessToken() throws Exception {
        mockMvc.perform(get("/api/v1/profiles"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_401"));
        mockMvc.perform(post("/api/v1/profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(profile("이름", "SENIOR"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void inactiveAccountCannotCreateProfile() throws Exception {
        long accountId = newAccount();
        String token = bearer(accountId);
        jdbcTemplate.update("UPDATE accounts SET status = 'INACTIVE' WHERE id = ?", accountId);

        mockMvc.perform(post("/api/v1/profiles").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(profile("이름", "SENIOR"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_005"));
    }

    @ParameterizedTest(name = "invalid request case {index}")
    @ValueSource(strings = {
            "{\"role\":\"SENIOR\"}",
            "{\"name\":\"   \",\"role\":\"SENIOR\"}",
            "{\"name\":\"이름\"}",
            "{\"name\":\"이름\",\"role\":\"ADMIN\"}",
            "{\"name\":\"이름\",\"role\":\"SENIOR\",\"gender\":\"OTHER\"}",
            "{\"name\":\"이름\",\"role\":\"SENIOR\",\"phoneNumber\":\"abc-1234\"}",
            "{\"name\":\"이름\",\"role\":\"SENIOR\",\"birthDate\":\"1950/03/15\"}"
    })
    void rejectsInvalidRequest(String body) throws Exception {
        mockMvc.perform(post("/api/v1/profiles").header(HttpHeaders.AUTHORIZATION, bearer(newAccount()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    void rejectsTooLongNameAndFutureBirthDate() throws Exception {
        long accountId = newAccount();
        create(accountId, profile("가".repeat(51), "SENIOR")).andExpect(status().isBadRequest());

        var future = profile("이름", "SENIOR");
        future.put("birthDate", LocalDate.now().plusDays(1).toString());
        create(accountId, future).andExpect(status().isBadRequest());
    }

    @Test
    void swaggerSendsBearerTokenOnlyToProtectedApis() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        var docs = jsonMapper.readTree(result.getResponse().getContentAsString());
        assertThat(docs.path("security").get(0).has("bearerAuth")).isTrue();

        // 프로필 API는 전역 설정을 그대로 따르고, 토큰 파라미터가 따로 노출되지 않는다.
        var profileApi = docs.path("paths").path("/api/v1/profiles");
        assertThat(profileApi.path("get").has("security")).isFalse();
        assertThat(profileApi.path("get").path("parameters").isEmpty()).isTrue();
        assertThat(profileApi.path("post").has("security")).isFalse();

        // 인증·Health API는 빈 security로 덮어써서 토큰을 보내지 않는다.
        for (String path : List.of("/api/v1/auth/signup", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout")) {
            assertThat(docs.path("paths").path(path).path("post").path("security").isEmpty()).as(path).isTrue();
            assertThat(docs.path("paths").path(path).path("post").has("security")).as(path).isTrue();
        }
        assertThat(docs.path("paths").path("/api/v1/health").path("get").has("security")).isTrue();
        assertThat(docs.path("paths").path("/api/v1/health").path("get").path("security").isEmpty()).isTrue();
    }

    private long newAccount() {
        Account account = accountRepository.save(new Account("profile-" + UUID.randomUUID() + "@example.com", "encoded"));
        accountIds.add(account.getId());
        return account.getId();
    }

    private String bearer(long accountId) {
        return "Bearer " + jwtTokenService.issue(accountId).accessToken();
    }

    private Map<String, String> profile(String name, String role) {
        var request = new LinkedHashMap<String, String>();
        request.put("name", name);
        request.put("role", role);
        return request;
    }

    private ResultActions create(long accountId, Map<String, String> request) throws Exception {
        return mockMvc.perform(post("/api/v1/profiles").header(HttpHeaders.AUTHORIZATION, bearer(accountId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)));
    }
}
