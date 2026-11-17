package com.ttareureung.api.global.health;

import com.ttareureung.api.global.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HealthController.class, properties = "jwt.secret=health-test-secret-key-at-least-32-bytes")
@Import(SecurityConfig.class)
class HealthApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthIsAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data").value("서버 정상 작동"));
    }

    @Test
    void missingPublicResourceReturnsNotFoundInsteadOfServerError() throws Exception {
        mockMvc.perform(get("/swagger-ui/does-not-exist.js"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_404"));
    }

    @Test
    void protectedPathsRemainBlocked() throws Exception {
        mockMvc.perform(get("/api/v1/seniors"))
                .andExpect(status().isUnauthorized());
    }
}
