package com.ttareureung.api.domain.senior;

import com.ttareureung.api.domain.medication.client.AiFileClient;
import com.ttareureung.api.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PillRecognitionApiTests extends ApiTestSupport {

    private static final String D = LocalDate.now(ZoneId.of("Asia/Seoul")).toString();
    private static final String MATCH = "{\"result\":\"MATCH\",\"reason\":\"흰색 원형 알약\"}";
    private static final byte[] IMAGE = {1, 2, 3};

    @MockitoBean
    private AiFileClient aiFileClient;

    @Test
    void matchThenIntakeRecordsVisionAndFinishesSession() throws Exception {
        Flow f = inProgressFlow("허리약");
        when(aiFileClient.askWithFile(any(), any(), any(), any())).thenReturn(MATCH);

        recognize(f.token, f.sessionId, f.medicationId, "image/jpeg", IMAGE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recognitionId").isNumber())
                .andExpect(jsonPath("$.data.medicationId").value(f.medicationId))
                .andExpect(jsonPath("$.data.result").value("MATCH"))
                .andExpect(jsonPath("$.data.message").value("맞는 약이에요!"))
                .andExpect(jsonPath("$.data.voiceText").value("맞는 약이에요!"))
                .andExpect(jsonPath("$.data.attemptsLeft").value(9));
        var row = jdbcTemplate.queryForMap("SELECT * FROM medication_recognitions WHERE dose_session_id = ?", f.sessionId);
        assertThat(row.get("result")).isEqualTo("MATCH");
        assertThat(row.get("reason")).isEqualTo("흰색 원형 알약");
        assertThat(((Number) row.get("medication_id")).longValue()).isEqualTo(f.medicationId);

        // 등록 정보가 프롬프트에 들어가는지
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(aiFileClient).askWithFile(any(), eq("image/jpeg"), eq(IMAGE), prompt.capture());
        assertThat(prompt.getValue()).contains("허리약").contains("흰색 원형 알약").contains("1정").contains("아침 식후");

        intake(f.token, f.sessionId, f.medicationId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("TAKEN"))
                .andExpect(jsonPath("$.data.verification").value("VISION"))
                .andExpect(jsonPath("$.data.takenAt").isNotEmpty())
                .andExpect(jsonPath("$.data.remainingCount").value(0))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.nextMedication").doesNotExist())
                .andExpect(jsonPath("$.data.voiceText").value("모든 약을 다 드셨어요."));
        var log = jdbcTemplate.queryForMap("SELECT * FROM medication_logs WHERE medication_id = ? AND target_date = ?", f.medicationId, D);
        assertThat(log.get("status")).isEqualTo("TAKEN");
        assertThat(log.get("verification")).isEqualTo("VISION");
        Object takenAt = log.get("taken_at");
        assertThat(takenAt).isNotNull();

        // 중복 복용 요청은 처음 기록 유지
        intake(f.token, f.sessionId, f.medicationId).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verification").value("VISION"));
        assertThat(jdbcTemplate.queryForMap("SELECT taken_at FROM medication_logs WHERE medication_id = ? AND target_date = ?",
                f.medicationId, D).get("taken_at")).isEqualTo(takenAt);

        getAs(f.token, "/api/v1/seniors/" + f.seniorId + "/schedules")
                .andExpect(jsonPath("$.data.sessions[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.takenCount").value(1));
    }

    @Test
    void mismatchUncertainAndFailuresNeverBlockTheFlow() throws Exception {
        Flow f = inProgressFlow("허리약");

        when(aiFileClient.askWithFile(any(), any(), any(), any())).thenReturn("{\"result\":\"MISMATCH\",\"reason\":\"다른 색\"}");
        recognize(f.token, f.sessionId, f.medicationId, "image/png", IMAGE)
                .andExpect(jsonPath("$.data.result").value("MISMATCH"))
                .andExpect(jsonPath("$.data.message").value("다른 약이에요. 다시 찾아주세요."));

        when(aiFileClient.askWithFile(any(), any(), any(), any())).thenReturn("```json\n{\"result\":\"uncertain\",\"reason\":\"흐림\"}\n```");
        recognize(f.token, f.sessionId, f.medicationId, "image/webp", IMAGE)
                .andExpect(jsonPath("$.data.result").value("UNCERTAIN"));

        when(aiFileClient.askWithFile(any(), any(), any(), any())).thenReturn("읽을 수 없어요");
        recognize(f.token, f.sessionId, f.medicationId, "image/jpeg", IMAGE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("UNCERTAIN"));

        when(aiFileClient.askWithFile(any(), any(), any(), any())).thenReturn("{\"result\":\"YES\"}");
        recognize(f.token, f.sessionId, f.medicationId, "image/jpeg", IMAGE)
                .andExpect(jsonPath("$.data.result").value("UNCERTAIN"));

        // 키 미설정·시간 초과처럼 호출 자체가 실패해도 UNCERTAIN으로 정상 응답
        when(aiFileClient.askWithFile(any(), any(), any(), any())).thenThrow(new IllegalStateException("GEMINI_API_KEY가 설정되지 않았습니다."));
        recognize(f.token, f.sessionId, f.medicationId, "image/jpeg", IMAGE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("UNCERTAIN"))
                .andExpect(jsonPath("$.data.attemptsLeft").value(5));
        assertThat(count("SELECT COUNT(*) FROM medication_recognitions WHERE dose_session_id = ?", f.sessionId)).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT reason FROM medication_recognitions WHERE dose_session_id = ? ORDER BY id DESC LIMIT 1", String.class, f.sessionId))
                .startsWith("판독 실패");

        // 직전 판독이 MATCH가 아니면 직접 고른 것(MANUAL)
        intake(f.token, f.sessionId, f.medicationId)
                .andExpect(jsonPath("$.data.verification").value("MANUAL"));
    }

    @Test
    void limitsRecognitionAttemptsPerMedication() throws Exception {
        Flow f = inProgressFlow("허리약");
        when(aiFileClient.askWithFile(any(), any(), any(), any())).thenReturn(MATCH);

        for (int i = 0; i < 10; i++) {
            recognize(f.token, f.sessionId, f.medicationId, "image/jpeg", IMAGE).andExpect(status().isOk());
        }
        recognize(f.token, f.sessionId, f.medicationId, "image/jpeg", IMAGE)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("SENIOR_007"));
        verify(aiFileClient, times(10)).askWithFile(any(), any(), any(), any());
        assertThat(count("SELECT COUNT(*) FROM medication_recognitions WHERE dose_session_id = ?", f.sessionId)).isEqualTo(10);
    }

    @Test
    void rejectsBadFilesAndWrongStatesWithoutCallingAi() throws Exception {
        Flow f = inProgressFlow("허리약");

        recognize(f.token, f.sessionId, f.medicationId, "text/plain", IMAGE)
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("MEDICATION_007"));
        recognize(f.token, f.sessionId, f.medicationId, "image/jpeg", new byte[0])
                .andExpect(status().isUnsupportedMediaType());
        recognize(f.token, f.sessionId, f.medicationId, "image/jpeg", new byte[5 * 1024 * 1024 + 1])
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("SENIOR_009"));
        mockMvc.perform(multipart(recognizeUrl(f.sessionId, f.medicationId)).header(HttpHeaders.AUTHORIZATION, f.token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));

        long otherMedication = registerMedication(f.accountId, f.seniorId, "저녁약", D, D, "DINNER");
        recognize(f.token, f.sessionId, otherMedication, "image/jpeg", IMAGE)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SENIOR_006"));
        intake(f.token, f.sessionId, otherMedication)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SENIOR_006"));

        Family other = newFamily();
        recognize(bearer(other.accountId()), f.sessionId, f.medicationId, "image/jpeg", IMAGE)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SENIOR_003"));
        intake(bearer(other.accountId()), f.sessionId, f.medicationId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SENIOR_003"));
        mockMvc.perform(multipart(recognizeUrl(f.sessionId, f.medicationId))
                        .file(new MockMultipartFile("file", "pill.jpg", "image/jpeg", IMAGE)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(aiFileClient);
        assertThat(count("SELECT COUNT(*) FROM medication_recognitions WHERE dose_session_id = ?", f.sessionId)).isZero();
    }

    @Test
    void requiresAnsweredSessionAndRejectsFinishedOne() throws Exception {
        Family fam = newFamily();
        String token = bearer(fam.accountId());
        long medicationId = registerMedication(fam.accountId(), fam.seniorId(), "허리약", D, D, "MORNING");
        passAllTimes(token, fam.seniorId());
        long sessionId = currentSessionId(token, fam.seniorId());

        // 전화 받기 전
        recognize(token, sessionId, medicationId, "image/jpeg", IMAGE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SENIOR_008"));
        intake(token, sessionId, medicationId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SENIOR_008"));

        // 미응답 3회로 끝난 회차
        for (int i = 0; i < 3; i++) {
            postJson(token, "/api/v1/dose-sessions/" + sessionId + "/no-answer", "").andExpect(status().isOk());
        }
        recognize(token, sessionId, medicationId, "image/jpeg", IMAGE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SENIOR_004"));
        intake(token, sessionId, medicationId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SENIOR_004"));
        verifyNoInteractions(aiFileClient);
    }

    @Test
    void intakeAdvancesToNextMedicationAndAnswerListShrinks() throws Exception {
        Family fam = newFamily();
        String token = bearer(fam.accountId());
        long first = registerMedication(fam.accountId(), fam.seniorId(), "허리약", D, D, "MORNING");
        long second = registerMedication(fam.accountId(), fam.seniorId(), "혈압약", D, D, "MORNING");
        passAllTimes(token, fam.seniorId());
        long sessionId = currentSessionId(token, fam.seniorId());
        postJson(token, "/api/v1/dose-sessions/" + sessionId + "/answer", "").andExpect(status().isOk());

        intake(token, sessionId, first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remainingCount").value(1))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.nextMedication.medicationId").value(second))
                .andExpect(jsonPath("$.data.nextMedication.name").value("혈압약"))
                .andExpect(jsonPath("$.data.nextMedication.voiceText").value(org.hamcrest.Matchers.startsWith("첫 번째 약이에요. 혈압약")))
                .andExpect(jsonPath("$.data.voiceText").value("잘 드셨어요. 다음 약을 찾아볼까요?"));

        postJson(token, "/api/v1/dose-sessions/" + sessionId + "/answer", "")
                .andExpect(jsonPath("$.data.medications.length()").value(1))
                .andExpect(jsonPath("$.data.medications[0].medicationId").value(second));
    }

    @Test
    void deletingSeniorRemovesRecognitions() throws Exception {
        Flow f = inProgressFlow("허리약");
        when(aiFileClient.askWithFile(any(), any(), any(), any())).thenReturn(MATCH);
        recognize(f.token, f.sessionId, f.medicationId, "image/jpeg", IMAGE).andExpect(status().isOk());

        deleteAs(f.token, "/api/v1/profiles/" + f.seniorId).andExpect(status().is2xxSuccessful());
        assertThat(count("SELECT COUNT(*) FROM medication_recognitions WHERE dose_session_id = ?", f.sessionId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM dose_sessions WHERE id = ?", f.sessionId)).isZero();
    }

    private record Flow(long accountId, long seniorId, String token, long medicationId, long sessionId) {}

    // 약 1개 등록 → 회차 시각 지남 → 전화 받은 상태
    private Flow inProgressFlow(String drugName) throws Exception {
        Family fam = newFamily();
        String token = bearer(fam.accountId());
        long medicationId = registerMedication(fam.accountId(), fam.seniorId(), drugName, D, D, "MORNING");
        passAllTimes(token, fam.seniorId());
        long sessionId = currentSessionId(token, fam.seniorId());
        postJson(token, "/api/v1/dose-sessions/" + sessionId + "/answer", "").andExpect(status().isOk());
        return new Flow(fam.accountId(), fam.seniorId(), token, medicationId, sessionId);
    }

    private void passAllTimes(String token, long seniorId) throws Exception {
        putJson(token, "/api/v1/seniors/" + seniorId + "/dose-times", Map.of("doseTimes", List.of(
                Map.of("timeSlot", "MORNING", "doseTime", "00:00"), Map.of("timeSlot", "LUNCH", "doseTime", "00:01"),
                Map.of("timeSlot", "DINNER", "doseTime", "00:02"), Map.of("timeSlot", "BEFORE_SLEEP", "doseTime", "00:03"))))
                .andExpect(status().isOk());
    }

    private long currentSessionId(String token, long seniorId) throws Exception {
        return jsonMapper.readTree(getAs(token, "/api/v1/seniors/" + seniorId + "/dose-sessions/current")
                .andReturn().getResponse().getContentAsString()).path("data").path("current").path("sessionId").asLong();
    }

    private ResultActions recognize(String token, long sessionId, long medicationId, String contentType, byte[] content) throws Exception {
        return mockMvc.perform(multipart(recognizeUrl(sessionId, medicationId))
                .file(new MockMultipartFile("file", "pill.jpg", contentType, content))
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    private ResultActions intake(String token, long sessionId, long medicationId) throws Exception {
        return postJson(token, "/api/v1/dose-sessions/" + sessionId + "/medications/" + medicationId + "/intake", "");
    }

    private static String recognizeUrl(long sessionId, long medicationId) {
        return "/api/v1/dose-sessions/" + sessionId + "/medications/" + medicationId + "/recognitions";
    }
}
