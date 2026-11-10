package com.ttareureung.api.global.config;

import com.ttareureung.api.global.health.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HealthController.class, properties = {
        "cors.allowed-origins=http://localhost:5173",
        "jwt.secret=security-test-secret-key-at-least-32-bytes"
})
@Import(SecurityConfig.class)
class AuthRequestSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void cookieAuthPostWithoutCustomHeaderIsRejectedBeforeController() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON_403"));
    }

    @Test
    void frontendPreflightAllowsCredentialsAndRequiredHeader() throws Exception {
        mockMvc.perform(options("/api/v1/auth/refresh")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Requested-With, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void unauthenticatedRequestIncludesBearerChallenge() throws Exception {
        mockMvc.perform(get("/api/v1/seniors"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("COMMON_401"));
    }
}
