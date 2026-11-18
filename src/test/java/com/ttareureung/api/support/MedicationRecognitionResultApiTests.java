package com.ttareureung.api.support;

import com.ttareureung.api.domain.medication.client.AiFileClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

class MedicationRecognitionResultApiTests extends ApiTestSupport {

    @MockitoBean
    private AiFileClient aiFileClient;

    private static final String FULL = """
            {"document_type":"처방전","document_box_2d":[20,100,60,500],
             "medications":[
               {"drug_name":"아로르신정","custom_drug_name":"관절 아플 때 먹는 약","category":"진통소염제",
                "strength":"25mg","effect":"관절염 통증 완화","intake_reason":"무릎 관절 통증 완화를 위한 처방",
                "storage_location":"냉장고 문 쪽 칸","frequency":"1일 2회, 식후 30분","dose_amount":"1정",
                "interval_days":1,"intake_timing":"AFTER_MEAL_30M","meal_time_slots":["MORNING","DINNER"],
                "confidence":98,
                "regions":[{"field":"DRUG_NAME","box_2d":[100,200,150,600]},
                           {"field":"FREQUENCY","box_2d":[400,100,450,300]}]},
               {"drug_name":"펠루스정","confidence":90}
             ]}""";

    @Test
    void fillsEasyNameReasonStorageStrengthRegionsAndOverallConfidence() throws Exception {
        when(aiFileClient.askWithFile(any(), any(), any(), any())).thenReturn(FULL);

        upload("image/jpeg")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.document_type").value("처방전"))
                .andExpect(jsonPath("$.data.document_region.field").value("DOCUMENT_TYPE"))
                .andExpect(jsonPath("$.data.document_region.x").value(0.1))
                .andExpect(jsonPath("$.data.document_region.y").value(0.02))
                .andExpect(jsonPath("$.data.document_region.width").value(0.4))
                .andExpect(jsonPath("$.data.document_region.height").value(0.04))
                .andExpect(jsonPath("$.data.overall_confidence").value(94))      // (98 + 90) / 2
                .andExpect(jsonPath("$.data.medications.length()").value(2))
                .andExpect(jsonPath("$.data.medications[0].custom_drug_name").value("관절 아플 때 먹는 약"))
                .andExpect(jsonPath("$.data.medications[0].category").value("진통소염제"))
                .andExpect(jsonPath("$.data.medications[0].strength").value("25mg"))
                .andExpect(jsonPath("$.data.medications[0].intake_reason").value("무릎 관절 통증 완화를 위한 처방"))
                .andExpect(jsonPath("$.data.medications[0].storage_location").value("냉장고 문 쪽 칸"))
                .andExpect(jsonPath("$.data.medications[0].regions.length()").value(2))
                .andExpect(jsonPath("$.data.medications[0].regions[0].field").value("DRUG_NAME"))
                .andExpect(jsonPath("$.data.medications[0].regions[0].x").value(0.2))
                .andExpect(jsonPath("$.data.medications[0].regions[0].y").value(0.1))
                .andExpect(jsonPath("$.data.medications[0].regions[0].width").value(0.4))
                .andExpect(jsonPath("$.data.medications[0].regions[0].height").value(0.05))
                .andExpect(jsonPath("$.data.medications[0].regions[1].field").value("FREQUENCY"))
                .andExpect(jsonPath("$.data.medications[1].regions.length()").value(0))
                .andExpect(jsonPath("$.data.medications[1].storage_location").isEmpty());
    }

    @Test
    void toleratesBadValuesByDroppingOrClippingThem() throws Exception {
        String ai = "{\"document_type\":\"null\",\"medications\":["
                + "{\"drug_name\":\"" + "가".repeat(150) + "\",\"intake_timing\":\"AFTER_DINNER\","
                + "\"interval_days\":99,\"confidence\":150,\"meal_time_slots\":[\"MORNING\",\"BRUNCH\"],"
                + "\"storage_location\":\"null\",\"regions\":["
                + "{\"field\":\"TITLE\",\"box_2d\":[1,1,2,2]},"                 // 허용하지 않은 필드
                + "{\"field\":\"DRUG_NAME\",\"box_2d\":[500,100,100,200]},"    // 뒤집힌 박스
                + "{\"field\":\"FREQUENCY\",\"box_2d\":[0,0,1200,10]},"        // 범위 밖
                + "{\"field\":\"INTAKE_METHOD\",\"box_2d\":[1,2,3]}]},"        // 좌표 개수 오류
                + "{\"custom_drug_name\":\"이름 없는 항목\"}]}";
        when(aiFileClient.askWithFile(any(), any(), any(), any())).thenReturn(ai);

        upload("image/png")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.document_type").isEmpty())
                .andExpect(jsonPath("$.data.medications.length()").value(1))   // 약 이름 없는 항목은 제외
                .andExpect(jsonPath("$.data.medications[0].drug_name").value("가".repeat(100)))
                .andExpect(jsonPath("$.data.medications[0].intake_timing").isEmpty())
                .andExpect(jsonPath("$.data.medications[0].interval_days").isEmpty())
                .andExpect(jsonPath("$.data.medications[0].confidence").value(100))
                .andExpect(jsonPath("$.data.medications[0].meal_time_slots.length()").value(1))
                .andExpect(jsonPath("$.data.medications[0].meal_time_slots[0]").value("MORNING"))
                .andExpect(jsonPath("$.data.medications[0].storage_location").isEmpty())
                .andExpect(jsonPath("$.data.medications[0].regions.length()").value(0))
                .andExpect(jsonPath("$.data.overall_confidence").value(100));
    }

    @Test
    void pdfHasNoRegions() throws Exception {
        when(aiFileClient.askWithFile(any(), any(), any(), any())).thenReturn(FULL);

        upload("application/pdf")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.document_region").isEmpty())
                .andExpect(jsonPath("$.data.medications[0].regions.length()").value(0))
                .andExpect(jsonPath("$.data.medications[0].strength").value("25mg"));
    }

    @Test
    void overallConfidenceIsNullWhenNoConfidenceWasGiven() throws Exception {
        when(aiFileClient.askWithFile(any(), any(), any(), any())).thenReturn("{\"medications\":[{\"drug_name\":\"약\"}]}");

        upload("image/jpeg")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overall_confidence").isEmpty())
                .andExpect(jsonPath("$.data.medications[0].confidence").isEmpty());
    }

    private ResultActions upload(String contentType) throws Exception {
        return mockMvc.perform(multipart("/api/v1/medications/recognition")
                .file(new MockMultipartFile("file", "rx", contentType, new byte[]{1}))
                .header(HttpHeaders.AUTHORIZATION, bearer(newAccount())));
    }
}
