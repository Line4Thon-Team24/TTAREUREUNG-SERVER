package com.ttareureung.api.domain.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.ttareureung.api.global.common.BaseResponse;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
@Import(AuthApiTests.ProtectedEndpoint.class)
class AuthApiTests {

    private static final String PASSWORD = "Password123!";
    private static final String SECRET = "auth-test-secret-key-at-least-32-bytes";
    private static final String COOKIE_NAME = "refresh_token";
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final List<Long> accountIds = new ArrayList<>();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanUpCreatedAccounts() {
        for (Long id : accountIds) {
            jdbcTemplate.update("DELETE FROM refresh_tokens WHERE account_id = ?", id);
            jdbcTemplate.update("DELETE FROM accounts WHERE id = ?", id);
        }
    }

    @Test
    void signupNormalizesEmailAndStoresOnlyHashedPassword() throws Exception {
        String email = newEmail();
        long id = signup("  " + email.toUpperCase(java.util.Locale.ROOT) + "  ");

        var row = jdbcTemplate.queryForMap("SELECT * FROM accounts WHERE id = ?", id);
        assertThat(row.get("email")).isEqualTo(email);
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(row.get("created_at")).isNotNull();
        assertThat(row.get("updated_at")).isNotNull();
        String hash = (String) row.get("password");
        assertThat(hash).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, hash)).isTrue();
    }

    @Test
    void duplicateNormalizedEmailReturnsConflict() throws Exception {
        String email = newEmail();
        signup(email);
        credentials("signup", " " + email.toUpperCase(java.util.Locale.ROOT) + " ", PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
    }

    @ParameterizedTest(name = "case {index}")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "invalid", "user@", "@example.com"})
    void rejectsInvalidEmail(String email) throws Exception {
        credentials("signup", email, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    void rejectsEmailLongerThan254Characters() throws Exception {
        String email = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(60) + ".com";
        credentials("signup", email, PASSWORD).andExpect(status().isBadRequest());
    }

    @ParameterizedTest(name = "case {index}")
    @NullAndEmptySource
    @ValueSource(strings = {"Abc123!", "Abcdefgh123456789012!", "abcdefgh!", "12345678!", "Password123",
            "Password 1!", " Password1!", "Password1!한"})
    void rejectsInvalidSignupPassword(String password) throws Exception {
        credentials("signup", newEmail(), password).andExpect(status().isBadRequest());
    }

    @ParameterizedTest(name = "case {index}")
    @ValueSource(strings = {"Abcd123!", "Abcd123!Abcd123!Abcd", "a1!@#$%^&*()", "a1~`[]{}\\|;:", "a1'\",.<>/?-_=+"})
    void acceptsPasswordLengthBoundariesAndSpecialCharacters(String password) throws Exception {
        signupWith(newEmail(), password);
    }

    @Test
    void loginAcceptsUpTo20CharacterPassword() throws Exception {
        String email = newEmail();
        String password = "Abcd123!Abcd123!Abcd";
        signupWith(email, password);

        credentials("login", email, password).andExpect(status().isOk());
        credentials("login", email, password + "a").andExpect(status().isBadRequest());
    }

    @Test
    void accountCreatedUnderOldPasswordRuleCanStillLogin() throws Exception {
        String email = newEmail();
        jdbcTemplate.update("INSERT INTO accounts (email, password, status, created_at, updated_at) VALUES (?, ?, 'ACTIVE', NOW(), NOW())",
                email, passwordEncoder.encode("Password123"));
        accountIds.add(jdbcTemplate.queryForObject("SELECT id FROM accounts WHERE email = ?", Long.class, email));

        credentials("login", email, "Password123").andExpect(status().isOk());
    }

    @Test
    void loginReturnsAccessTokenAndHttpOnlyRefreshCookie() throws Exception {
        String email = newEmail();
        long id = signup(email);
        MvcResult result = login(" " + email.toUpperCase(java.util.Locale.ROOT) + " ");
        var body = jsonMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(body.path("accessToken").asString()).isNotBlank();
        assertThat(body.path("tokenType").asString()).isEqualTo("Bearer");
        assertThat(body.path("expiresIn").asLong()).isEqualTo(1800);
        assertThat(body.has("refreshToken")).isFalse();

        String header = refreshHeader(result);
        assertThat(header).contains("HttpOnly", "Path=/api/v1/auth", "SameSite=Lax", "Max-Age=604800");
        assertThat(header).doesNotContain("Secure", "Domain=");
        String refresh = refreshCookie(result).getValue();
        String hash = jdbcTemplate.queryForObject("SELECT token_hash FROM refresh_tokens WHERE account_id = ?", String.class, id);
        assertThat(hash).isEqualTo(hash(refresh)).isNotEqualTo(refresh);
    }

    @Test
    void wrongPasswordAndUnknownEmailReturnSameUnauthorizedResponse() throws Exception {
        String email = newEmail();
        signup(email);
        credentials("login", email, "Wrongpass123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_001"));
        credentials("login", newEmail(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_001"));
    }

    @Test
    void inactiveAccountCannotLoginOrRefresh() throws Exception {
        String email = newEmail();
        long id = signup(email);
        Cookie refresh = refreshCookie(login(email));
        jdbcTemplate.update("UPDATE accounts SET status = 'INACTIVE' WHERE id = ?", id);
        credentials("login", email, PASSWORD).andExpect(status().isForbidden());
        tokenRequest("refresh", refresh).andExpect(status().isForbidden());
    }

    @Test
    void refreshRotatesTokenAndRejectsPreviouslyUsedToken() throws Exception {
        String email = newEmail();
        long id = signup(email);
        Cookie previous = refreshCookie(login(email));
        MvcResult result = tokenRequest("refresh", previous)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty()).andReturn();
        Cookie next = refreshCookie(result);
        assertThat(next.getValue()).isNotEqualTo(previous.getValue());
        assertThat(countHash(previous.getValue())).isZero();
        assertThat(countHash(next.getValue())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE account_id = ?", Long.class, id)).isEqualTo(1);
        tokenRequest("refresh", previous).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsExpiredRefreshToken() throws Exception {
        long id = signup(newEmail());
        String token = signedToken(id, "refresh", Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600));
        storeRefresh(id, token, Instant.now().minusSeconds(3600));
        tokenRequest("refresh", new Cookie(COOKIE_NAME, token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    @Test
    void rejectsRefreshTokenWhoseDatabaseExpiryHasPassed() throws Exception {
        long id = signup(newEmail());
        String token = signedToken(id, "refresh", Instant.now(), Instant.now().plusSeconds(3600));
        storeRefresh(id, token, Instant.now().minusSeconds(3600));
        tokenRequest("refresh", new Cookie(COOKIE_NAME, token)).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTamperedRefreshToken() throws Exception {
        String email = newEmail();
        signup(email);
        String[] parts = refreshCookie(login(email)).getValue().split("\\.");
        parts[2] = (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        tokenRequest("refresh", new Cookie(COOKIE_NAME, String.join(".", parts)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutDeletesOnlyCurrentTokenAndExpiresCookie() throws Exception {
        String email = newEmail();
        signup(email);
        MvcResult first = login(email);
        Cookie current = refreshCookie(first);
        Cookie otherDevice = refreshCookie(login(email));
        MvcResult logout = tokenRequest("logout", current)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true)).andReturn();
        assertThat(refreshHeader(logout)).contains("Max-Age=0", "Path=/api/v1/auth", "HttpOnly");
        assertThat(countHash(current.getValue())).isZero();
        assertThat(countHash(otherDevice.getValue())).isEqualTo(1);
        tokenRequest("refresh", current).andExpect(status().isUnauthorized());
        tokenRequest("refresh", otherDevice).andExpect(status().isOk());
        tokenRequest("logout", current).andExpect(status().isOk());

        String access = jsonMapper.readTree(first.getResponse().getContentAsString()).path("data").path("accessToken").asString();
        mockMvc.perform(get("/test/authenticated").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isOk());
    }

    @Test
    void refreshRequiresRefreshCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh").header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedApiRequiresAccessTokenAndRejectsRefreshTokenAsBearer() throws Exception {
        mockMvc.perform(get("/test/authenticated"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_401"));
        String email = newEmail();
        long id = signup(email);
        MvcResult login = login(email);
        String access = jsonMapper.readTree(login.getResponse().getContentAsString()).path("data").path("accessToken").asString();
        mockMvc.perform(get("/test/authenticated").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(String.valueOf(id)));
        mockMvc.perform(get("/test/authenticated").header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshCookie(login).getValue()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingCustomHeaderCannotTriggerCookieAuthOperations() throws Exception {
        String email = newEmail();
        signup(email);
        Cookie token = refreshCookie(login(email));
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(token)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/auth/logout").cookie(token)).andExpect(status().isForbidden());
        assertThat(countHash(token.getValue())).isEqualTo(1);
    }

    @Test
    void jsonSignupAndLoginDoNotRequireCustomHeader() throws Exception {
        String email = newEmail();
        MvcResult signup = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON).content(requestJson(email, PASSWORD)))
                .andExpect(status().isCreated()).andReturn();
        accountIds.add(jsonMapper.readTree(signup.getResponse().getContentAsString()).path("data").path("accountId").asLong());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(requestJson(email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void swaggerRequiresHeaderOnlyForCookieAuthEndpoints() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        var paths = jsonMapper.readTree(result.getResponse().getContentAsString()).path("paths");
        for (String path : List.of("signup", "login", "refresh", "logout")) {
            boolean hasHeader = false;
            for (var parameter : paths.path("/api/v1/auth/" + path).path("post").path("parameters")) {
                if ("X-Requested-With".equals(parameter.path("name").asString())) {
                    hasHeader = true;
                    assertThat(parameter.path("required").asBoolean()).isTrue();
                }
            }
            assertThat(hasHeader).as(path).isEqualTo(List.of("refresh", "logout").contains(path));
        }
    }

    @ParameterizedTest(name = "simple content type case {index}")
    @ValueSource(strings = {"text/plain", "application/x-www-form-urlencoded", "multipart/form-data"})
    void signupAndLoginRejectSimpleContentTypesWithoutCustomHeader(String contentType) throws Exception {
        for (String path : List.of("signup", "login")) {
            mockMvc.perform(post("/api/v1/auth/" + path)
                            .contentType(contentType).content(requestJson(newEmail(), PASSWORD)))
                    .andExpect(status().isUnsupportedMediaType());
        }
    }

    @Test
    void rejectsAuthRequestFromUnapprovedOrigin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .header(HttpHeaders.ORIGIN, "http://untrusted.example")
                        .contentType(MediaType.APPLICATION_JSON).content(requestJson(newEmail(), PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void malformedJsonAndUnsupportedContentTypeReturnClientErrors() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/auth/signup")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.TEXT_PLAIN).content(requestJson(newEmail(), PASSWORD)))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void refreshCanRecoverEvenIfClientSendsExpiredAccessToken() throws Exception {
        String email = newEmail();
        long id = signup(email);
        Cookie refresh = refreshCookie(login(email));
        String expired = signedToken(id, "access", Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600));
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refresh)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isOk());
    }

    @Test
    void concurrentSignupForSameEmailReturnsOneCreatedAndOneConflict() throws Exception {
        String email = newEmail();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var task = (java.util.concurrent.Callable<MvcResult>) () -> {
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("동시 요청 시작 실패");
                }
                return credentials("signup", email, PASSWORD).andReturn();
            };
            var first = executor.submit(task);
            var second = executor.submit(task);
            start.countDown();
            MvcResult a = first.get(20, TimeUnit.SECONDS);
            MvcResult b = second.get(20, TimeUnit.SECONDS);
            MvcResult winner = a.getResponse().getStatus() == 201 ? a : b;
            long id = jsonMapper.readTree(winner.getResponse().getContentAsString()).path("data").path("accountId").asLong();
            if (id > 0) {
                accountIds.add(id);
            }
            assertThat(List.of(a.getResponse().getStatus(), b.getResponse().getStatus()))
                    .containsExactlyInAnyOrder(201, 409);
        }
    }

    @Test
    void onlyOneConcurrentRefreshCanConsumeToken() throws Exception {
        String email = newEmail();
        signup(email);
        Cookie token = refreshCookie(login(email));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var task = (java.util.concurrent.Callable<MvcResult>) () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("동시 요청 시작 실패");
                }
                return tokenRequest("refresh", token).andReturn();
            };
            var first = executor.submit(task);
            var second = executor.submit(task);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            MvcResult a = first.get(20, TimeUnit.SECONDS);
            MvcResult b = second.get(20, TimeUnit.SECONDS);
            assertThat(List.of(a.getResponse().getStatus(), b.getResponse().getStatus()))
                    .containsExactlyInAnyOrder(200, 401);
            MvcResult winner = a.getResponse().getStatus() == 200 ? a : b;
            assertThat(countHash(refreshCookie(winner).getValue())).isEqualTo(1);
            assertThat(countHash(token.getValue())).isZero();
        }
    }

    private long signup(String email) throws Exception {
        return signupWith(email, PASSWORD);
    }

    private long signupWith(String email, String password) throws Exception {
        MvcResult result = credentials("signup", email, password)
                .andExpect(status().isCreated()).andReturn();
        long id = jsonMapper.readTree(result.getResponse().getContentAsString()).path("data").path("accountId").asLong();
        accountIds.add(id);
        return id;
    }

    private MvcResult login(String email) throws Exception {
        return credentials("login", email, PASSWORD)
                .andExpect(status().isOk()).andReturn();
    }

    private org.springframework.test.web.servlet.ResultActions credentials(String path, String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/" + path)
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON).content(requestJson(email, password)));
    }

    private org.springframework.test.web.servlet.ResultActions tokenRequest(String path, Cookie cookie) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/" + path)
                .header("X-Requested-With", "XMLHttpRequest").cookie(cookie));
    }

    private String requestJson(String email, String password) {
        var request = new LinkedHashMap<String, String>();
        request.put("email", email);
        request.put("password", password);
        return jsonMapper.writeValueAsString(request);
    }

    private String newEmail() {
        return "auth-" + UUID.randomUUID() + "@example.com";
    }

    private String refreshHeader(MvcResult result) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith(COOKIE_NAME + "="))
                .findFirst().orElseThrow();
    }

    private Cookie refreshCookie(MvcResult result) {
        String value = refreshHeader(result).split(";", 2)[0].substring(COOKIE_NAME.length() + 1);
        return new Cookie(COOKIE_NAME, value);
    }

    private String hash(String token) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
    }

    private long countHash(String token) throws Exception {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE token_hash = ?", Long.class, hash(token));
    }

    private void storeRefresh(long accountId, String token, Instant expiresAt) throws Exception {
        jdbcTemplate.update("INSERT INTO refresh_tokens (account_id, token_hash, expires_at, created_at) VALUES (?, ?, ?, ?)",
                accountId, hash(token), LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC),
                LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));
    }

    private String signedToken(long accountId, String type, Instant issuedAt, Instant expiresAt) {
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(SECRET.getBytes(StandardCharsets.UTF_8)));
        var claims = JwtClaimsSet.builder().issuer("ttareureung").subject(String.valueOf(accountId))
                .issuedAt(issuedAt).expiresAt(expiresAt).id(UUID.randomUUID().toString())
                .claim("token_type", type).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    @RestController
    static class ProtectedEndpoint {
        @GetMapping("/test/authenticated")
        BaseResponse<String> authenticated(@AuthenticationPrincipal Jwt jwt) {
            return BaseResponse.success(jwt.getSubject());
        }
    }
}
