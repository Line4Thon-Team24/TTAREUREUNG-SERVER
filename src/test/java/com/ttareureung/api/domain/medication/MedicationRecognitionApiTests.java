package com.ttareureung.api.domain.medication;

import com.ttareureung.api.domain.medication.client.AiFileClient;
import com.ttareureung.api.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MedicationRecognitionApiTests extends ApiTestSupport {

    private static final String RESULT = """
            {"medications":[{"drug_name":"아로르신정 25밀리그램","effect":"관절염 통증 완화",
            "frequency":"1일 2회, 아침/저녁 식후 복용","dose_amount":"1정","meal_time_slots":["MORNING","DINNER"],"confidence":99}]}""";

    @MockitoBean
    private AiFileClient aiFileClient;

    @Test
    void returnsRecognizedMedications() throws Exception {
        when(aiFileClient.askWithFile(any(), any(), any(), any())).thenReturn(RESULT);

        upload(bearer(newAccount()), "image/jpeg", new byte[]{1, 2, 3})
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.medications.length()").value(1))
                .andExpect(jsonPath("$.data.medications[0].drug_name").value("아로르신정 25밀리그램"))
                .andExpect(jsonPath("$.data.medications[0].meal_time_slots[1]").value("DINNER"))
                .andExpect(jsonPath("$.data.medications[0].dose_amount").value("1정"))
                .andExpect(jsonPath("$.data.medications[0].confidence").value(99));
        verify(aiFileClient).askWithFile(any(), eq("image/jpeg"), any(), any());
    }

    @Test
    void stripsMarkdownFenceAroundJson() throws Exception {
        when(aiFileClient.askWithFile(any(), any(), any(), any()))
                .thenReturn("```json\n" + RESULT + "\n```");

        upload(bearer(newAccount()), "application/pdf", new byte[]{1})
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.medications[0].drug_name").value("아로르신정 25밀리그램"));
    }

    @Test
    void emptyResultReturnsUnprocessable() throws Exception {
        when(aiFileClient.askWithFile(any(), any(), any(), any())).thenReturn("{\"medications\":[]}");

        upload(bearer(newAccount()), "image/png", new byte[]{1})
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MEDICATION_008"));
    }

    @Test
    void unparsableAiResponseReturnsBadGateway() throws Exception {
        when(aiFileClient.askWithFile(any(), any(), any(), any())).thenReturn("읽을 수 없습니다.");

        upload(bearer(newAccount()), "image/jpeg", new byte[]{1})
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("MEDICATION_010"));
    }

    @Test
    void aiCallFailureReturnsBadGateway() throws Exception {
        when(aiFileClient.askWithFile(any(), any(), any(), any())).thenThrow(new IllegalStateException("boom"));

        upload(bearer(newAccount()), "image/jpeg", new byte[]{1})
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("MEDICATION_009"));
    }

    @Test
    void rejectsUnsupportedOrEmptyFileWithoutCallingAi() throws Exception {
        String token = bearer(newAccount());

        upload(token, "text/plain", new byte[]{1})
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("MEDICATION_007"));
        upload(token, "image/jpeg", new byte[0])
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(aiFileClient);
    }

    @Test
    void requiresAccessToken() throws Exception {
        mockMvc.perform(multipart("/api/v1/medications/recognition")
                        .file(new MockMultipartFile("file", "rx", "image/jpeg", new byte[]{1})))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions upload(String token, String contentType, byte[] content) throws Exception {
        return mockMvc.perform(multipart("/api/v1/medications/recognition")
                .file(new MockMultipartFile("file", "rx", contentType, content))
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    @Test
    void returnsCategoryTimingAndSanitizedFileName() throws Exception {
        when(aiFileClient.askWithFile(any(), any(), any(), any())).thenReturn("""
            {"medications":[{"drug_name":"아로르신정","category":"진통소염제","effect":"통증 완화",
            "frequency":"1일 3회, 식후 30분","dose_amount":"1정","interval_days":1,
            "intake_timing":"AFTER_MEAL_30M","meal_time_slots":["MORNING","BEFORE_SLEEP"],"confidence":98}]}""");

        mockMvc.perform(multipart("/api/v1/medications/recognition")
                        .file(new MockMultipartFile("file", "../../etc/처방전.jpg", "image/jpeg", new byte[]{1}))
                        .header(HttpHeaders.AUTHORIZATION, bearer(newAccount())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.file_name").value("처방전.jpg"))   // 경로 제거
                .andExpect(jsonPath("$.data.medications[0].category").value("진통소염제"))
                .andExpect(jsonPath("$.data.medications[0].intake_timing").value("AFTER_MEAL_30M"))
                .andExpect(jsonPath("$.data.medications[0].interval_days").value(1))
                .andExpect(jsonPath("$.data.medications[0].meal_time_slots[1]").value("BEFORE_SLEEP"));
    }

    @Test
    void promptListsBedtimeSlotAndNewFields() throws Exception {
        when(aiFileClient.askWithFile(any(), any(), any(), any())).thenReturn("{\"medications\":[{\"drug_name\":\"약\"}]}");

        mockMvc.perform(multipart("/api/v1/medications/recognition")
                        .file(new MockMultipartFile("file", "rx.jpg", "image/jpeg", new byte[]{1}))
                        .header(HttpHeaders.AUTHORIZATION, bearer(newAccount())))
                .andExpect(status().isOk());

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(aiFileClient).askWithFile(system.capture(), any(), any(), any());
        assertThat(system.getValue()).contains("BEFORE_SLEEP", "intake_timing", "interval_days", "category");
    }
}