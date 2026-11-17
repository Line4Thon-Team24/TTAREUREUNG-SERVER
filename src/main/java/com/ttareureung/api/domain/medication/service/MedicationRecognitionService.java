package com.ttareureung.api.domain.medication.service;

import tools.jackson.databind.json.JsonMapper;
import com.ttareureung.api.domain.medication.client.AiFileClient;
import com.ttareureung.api.domain.medication.dto.RecognitionResponse;
import com.ttareureung.api.domain.medication.exception.MedicationErrorCode;
import com.ttareureung.api.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicationRecognitionService {

    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "application/pdf");

    private static final String SYSTEM_PROMPT = """
            너는 처방전·약봉투·약 사진에서 복약 정보를 추출하는 도구다.
            - 이미지에 실제로 적힌 내용만 추출하고, 보이지 않는 값은 추측하지 말고 null로 둔다.
            - 약마다 항목 하나를 만든다.
            - category는 약의 종류를 "진통소염제", "혈압약"처럼 짧은 한국어 명사로 적고, 알 수 없으면 null로 둔다.
            - meal_time_slots는 MORNING, LUNCH, DINNER, BEFORE_SLEEP 중에서만 고르고, 알 수 없으면 빈 배열로 둔다.
            - intake_timing은 BEFORE_MEAL_30M(식사 30분 전), AFTER_MEAL_30M(식사 30분 후), RIGHT_AFTER_MEAL(식사 직후), BEFORE_BED(취침 전) 중 하나로 적고, 알 수 없으면 null로 둔다.
            - interval_days는 복용 간격을 일 단위 정수로 적는다. 매일이면 1, 이틀에 한 번이면 2, 일주일에 한 번이면 7이고, 알 수 없으면 null로 둔다.
            - frequency는 "하루 3회, 식후 30분" 같은 한국어 문장으로 적는다.
            - dose_amount는 1회 복용량을 "2정", "1포", "5ml"처럼 숫자와 단위로 적고, 없으면 null로 둔다.
            - confidence는 0~100 정수로, 글자를 얼마나 확실하게 읽었는지 적는다.
            - 약 정보가 전혀 없는 이미지면 {"medications": []} 를 반환한다.
            - 설명이나 마크다운 없이 JSON 객체만 출력한다.
            형식: {"medications":[{"drug_name":"","category":"","effect":"","frequency":"","dose_amount":"","interval_days":1,"intake_timing":"","meal_time_slots":[],"confidence":0}]}
            """;

    private final AiFileClient aiFileClient;
    private final JsonMapper jsonMapper; // Boot 4는 Jackson 3: tools.jackson.databind.json.JsonMapper

    public RecognitionResponse recognize(MultipartFile file) {
        String mediaType = file.getContentType();

        if (file.isEmpty() || mediaType == null || !ALLOWED_TYPES.contains(mediaType)) {
            throw new CustomException(MedicationErrorCode.UNSUPPORTED_FILE_TYPE);
        }

        String raw;
        try {
            raw = aiFileClient.askWithFile(
                    SYSTEM_PROMPT,
                    mediaType,
                    file.getBytes(),
                    "이 파일에서 복약 정보를 추출해줘."
            );
        } catch (Exception e) {
            log.error("AI 인식 호출 실패", e);
            throw new CustomException(MedicationErrorCode.AI_RECOGNITION_FAILED);
        }

        RecognitionResponse result;
        try {
            String json = raw.strip()
                    .replaceAll("^```(?:json)?\\s*", "")
                    .replaceAll("\\s*```$", "");

            result = jsonMapper.readValue(json, RecognitionResponse.class);
        } catch (Exception e) {
            log.error("AI 응답 파싱 실패: {}", raw, e);
            throw new CustomException(MedicationErrorCode.AI_RESPONSE_INVALID);
        }

        if (result == null || result.medications() == null || result.medications().isEmpty()) {
            throw new CustomException(MedicationErrorCode.NO_MEDICATION_RECOGNIZED);
        }

        return new RecognitionResponse(safeFileName(file.getOriginalFilename()), result.medications());
    }

    private static String safeFileName(String original) {
        if (original == null) {
            return null;
        }
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty()) {
            return null;
        }
        return name.length() > 255 ? name.substring(0, 255) : name;
    }
}
