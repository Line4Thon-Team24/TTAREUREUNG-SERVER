package com.ttareureung.api.domain.file;

import com.ttareureung.api.domain.file.storage.LocalFileStorage;
import com.ttareureung.api.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileApiTests extends ApiTestSupport {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};

    @Autowired
    private LocalFileStorage localStorage;

    @Test
    void uploadsAndServesThroughSignedLinkWithoutToken() throws Exception {
        String token = bearer(newAccount());

        MvcResult result = upload(token, "pill.jpg", "image/jpeg", JPEG)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fileKey").value(startsWith("uploads/")))
                .andExpect(jsonPath("$.data.fileKey").value(org.hamcrest.Matchers.endsWith(".jpg")))
                .andExpect(jsonPath("$.data.url").value(startsWith("http://localhost:8080/api/v1/files/local/uploads/")))
                .andExpect(jsonPath("$.data.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.data.size").value(JPEG.length))
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty())
                .andReturn();
        JsonNode data = jsonMapper.readTree(result.getResponse().getContentAsString()).path("data");
        String url = data.path("url").asText();
        // 파일 이름은 버리고 무작위 키로 저장
        assertThat(data.path("fileKey").asText()).doesNotContain("pill");

        mockMvc.perform(get(pathAndQuery(url)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
                .andExpect(content().bytes(JPEG));
    }

    @Test
    void rejectsTamperedExpiredOrUnknownLinks() throws Exception {
        String token = bearer(newAccount());
        String url = jsonMapper.readTree(upload(token, "a.png", "image/png", JPEG).andReturn().getResponse().getContentAsString())
                .path("data").path("url").asText();
        String fileKey = jsonMapper.readTree(upload(token, "b.png", "image/png", JPEG).andReturn().getResponse().getContentAsString())
                .path("data").path("fileKey").asText();

        mockMvc.perform(get(pathAndQuery(url) + "x"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FILE_002"));
        mockMvc.perform(get(pathAndQuery(url).replaceFirst("exp=\\d+", "exp=9999999999")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FILE_002"));
        mockMvc.perform(get(pathAndQuery(localStorage.url(fileKey, Duration.ofMinutes(-1)))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FILE_002"));
        mockMvc.perform(get(pathAndQuery(localStorage.url("uploads/2026/11/none.png", Duration.ofMinutes(5)))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FILE_001"));
        // 상위 폴더 탈출은 Spring 요청 방화벽(400) 또는 저장소 경로 검사(404)에서 차단
        mockMvc.perform(get(pathAndQuery(localStorage.url("../../etc/passwd", Duration.ofMinutes(5)))))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/v1/files/local/uploads/x.png"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnsupportedFilesAndRequiresToken() throws Exception {
        String token = bearer(newAccount());

        upload(token, "note.txt", "text/plain", JPEG)
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("MEDICATION_007"));
        upload(token, "empty.jpg", "image/jpeg", new byte[0])
                .andExpect(status().isUnsupportedMediaType());
        mockMvc.perform(multipart("/api/v1/files").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
        mockMvc.perform(multipart("/api/v1/files").file(new MockMultipartFile("file", "p.jpg", "image/jpeg", JPEG)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void medicationResponsesTurnFileKeysIntoLinksAndKeepExternalUrls() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        String fileKey = jsonMapper.readTree(upload(token, "p.jpg", "image/jpeg", JPEG).andReturn().getResponse().getContentAsString())
                .path("data").path("fileKey").asText();
        String today = LocalDate.now(ZoneId.of("Asia/Seoul")).toString();

        var withKey = medicationItem("허리약", today, today, "MORNING");
        withKey.put("image_url", fileKey);
        withKey.put("senior_profile_id", f.seniorId());
        postJson(token, "/api/v1/medications", withKey).andExpect(status().isCreated());
        var withUrl = medicationItem("혈압약", today, today, "MORNING");
        withUrl.put("image_url", "https://example.com/pill.png");
        withUrl.put("senior_profile_id", f.seniorId());
        postJson(token, "/api/v1/medications", withUrl).andExpect(status().isCreated());

        // 약 목록(보호자 화면): 최신 등록 순
        getAs(token, "/api/v1/medications?senior_id=" + f.seniorId())
                .andExpect(jsonPath("$.data.medications[0].image_url").value("https://example.com/pill.png"))
                .andExpect(jsonPath("$.data.medications[1].image_url").value(startsWith("http://localhost:8080/api/v1/files/local/" + fileKey + "?exp=")));
        assertThat(jdbcTemplate.queryForObject("SELECT image_url FROM medication WHERE custom_drug_name IS NULL AND drug_name = '허리약' AND senior_profile_id = ?",
                String.class, f.seniorId())).isEqualTo(fileKey);

        // 전화 흐름(시니어 화면)
        putJson(token, "/api/v1/seniors/" + f.seniorId() + "/dose-times", Map.of("doseTimes", List.of(
                Map.of("timeSlot", "MORNING", "doseTime", "00:00"), Map.of("timeSlot", "LUNCH", "doseTime", "00:01"),
                Map.of("timeSlot", "DINNER", "doseTime", "00:02"), Map.of("timeSlot", "BEFORE_SLEEP", "doseTime", "00:03"))))
                .andExpect(status().isOk());
        getAs(token, "/api/v1/seniors/" + f.seniorId() + "/dose-sessions/current")
                .andExpect(jsonPath("$.data.current.medications[0].imageUrl").value(startsWith("http://localhost:8080/api/v1/files/local/")))
                .andExpect(jsonPath("$.data.current.medications[1].imageUrl").value("https://example.com/pill.png"));
    }

    private ResultActions upload(String token, String name, String contentType, byte[] content) throws Exception {
        return mockMvc.perform(multipart("/api/v1/files")
                .file(new MockMultipartFile("file", name, contentType, content))
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    private static String pathAndQuery(String url) {
        URI uri = URI.create(url);
        return uri.getRawPath() + "?" + uri.getRawQuery();
    }
}
