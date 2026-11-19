package com.ttareureung.api.domain.auth;

import com.ttareureung.api.domain.user.entity.Account;
import com.ttareureung.api.domain.user.repository.AccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 배포 서버 기준(application.yml 기본값과 동일): 다른 도메인 프론트에서도 refresh 쿠키가 전송되도록 SameSite=None; Secure
@SpringBootTest(properties = {
        "jwt.secret=auth-test-secret-key-at-least-32-bytes",
        "jwt.cookie.same-site=None",
        "jwt.cookie.secure=true"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class RefreshCookieDefaultsTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AccountRepository accounts;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long accountId;

    @AfterEach
    void cleanUp() {
        if (accountId != null) {
            jdbcTemplate.update("DELETE FROM refresh_tokens WHERE account_id = ?", accountId);
            jdbcTemplate.update("DELETE FROM accounts WHERE id = ?", accountId);
        }
    }

    @Test
    void loginSetsCrossSiteRefreshCookie() throws Exception {
        String email = "cookie-" + UUID.randomUUID() + "@example.com";
        accountId = accounts.save(new Account(email, passwordEncoder.encode("Password123!"))).getId();

        String header = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Password123!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertThat(header).contains("refresh_token=", "HttpOnly", "Secure", "SameSite=None", "Path=/api/v1/auth");
    }
}
