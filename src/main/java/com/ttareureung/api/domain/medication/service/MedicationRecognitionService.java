package com.ttareureung.api.domain.medication.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ttareureung.api.domain.medication.entity.IntakeTiming;
import com.ttareureung.api.domain.medication.entity.MealTimeSlot;
import tools.jackson.databind.json.JsonMapper;
import com.ttareureung.api.domain.medication.client.AiFileClient;
import com.ttareureung.api.domain.medication.dto.RecognitionResponse;
import com.ttareureung.api.domain.medication.exception.MedicationErrorCode;
import com.ttareureung.api.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicationRecognitionService {

    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "application/pdf");
    private static final Set<String> REGION_FIELDS = Set.of("DRUG_NAME", "INTAKE_METHOD", "FREQUENCY");

    // "하루 2회", "1일 3회", "1일2회", "매일 1번", "하루 두 번" 등에서 횟수를 뽑는다
    private static final Pattern TIMES_PER_DAY =
            Pattern.compile("(?:하루|1\\s*일|매일)\\s*(?:에)?\\s*(\\d+|한|두|세|네)\\s*(?:회|번)");

    private static final String SYSTEM_PROMPT = """
            너는 처방전·약봉투·약 사진에서 복약 정보를 추출하는 도구다.
            - 이미지에 실제로 적힌 내용만 추출하고, 보이지 않는 값은 추측하지 말고 null로 둔다.
            - 약마다 항목 하나를 만든다.
            - drug_name은 약 이름을 문서에 적힌 그대로 적는다.
            - strength는 함량을 "5mg", "500mg"처럼 숫자와 단위(mg, g, ml 등)로 적는다. "25밀리그램"처럼 적혀 있으면 "25mg"로 바꾼다. 없으면 null로 둔다.
            - category는 약의 종류를 "진통소염제", "혈압약"처럼 짧은 한국어 명사로 적고, 알 수 없으면 null로 둔다.
            - effect는 문서에 적힌 효능·효과를 짧게 적고, 없으면 null로 둔다.
            - custom_drug_name은 시니어가 쉽게 알아볼 이름("허리 통증 줄여주는 약")으로, 문서에 적힌 효능·효과 문구에서만 만든다. 효능·효과가 없으면 null로 둔다. 병명이나 진단을 추측하지 않는다.
            - intake_reason은 문서에 적힌 처방 목적·복용 이유 문구만 옮긴다. 없으면 null로 두고 추측하지 않는다.
            - storage_location은 문서에 약을 두는 장소가 명시된 경우에만 적는다. 보관 온도 안내나 일반 주의 문구는 쓰지 않는다. 대부분 null이다.
            - meal_time_slots는 반드시 영문 값 MORNING(아침·조식), LUNCH(점심·중식), DINNER(저녁·석식), BEFORE_SLEEP(취침 전·자기 전) 중에서 고른다. 약봉투나 처방전에 아침/점심/저녁/취침전 칸의 체크·동그라미·숫자 표시가 있거나 "아침 저녁 식후"처럼 끼니가 글자로 적혀 있으면 그 끼니를 모두 넣는다. 끼니가 문서에 전혀 없으면 빈 배열로 둔다.
            - intake_timing은 BEFORE_MEAL_30M(식사 30분 전), AFTER_MEAL_30M(식사 30분 후), RIGHT_AFTER_MEAL(식사 직후), BEFORE_BED(취침 전) 중 하나로 적고, 알 수 없으면 null로 둔다.
            - interval_days는 복용 간격을 일 단위 정수로 적는다. 매일이면 1, 이틀에 한 번이면 2, 일주일에 한 번이면 7이고, 알 수 없으면 null로 둔다.
            - frequency는 "하루 3회, 식후 30분" 같은 한국어 문장으로 적는다.
            - dose_amount는 1회 복용량을 "2정", "1포", "5ml"처럼 숫자와 단위로 적고, 없으면 null로 둔다.
            - confidence는 0~100 정수로, 글자를 얼마나 확실하게 읽었는지 적는다.
            - document_type은 문서 종류("처방전", "약봉투", "약 사진" 등)이고, document_box_2d는 그 글자의 위치다.
            - regions에는 약마다 읽은 글자의 위치를 넣는다. field는 DRUG_NAME(약품명), INTAKE_METHOD(복용법), FREQUENCY(복용 횟수) 중 하나이고, box_2d는 [ymin, xmin, ymax, xmax] 정수 네 개로 이미지 전체를 1000으로 본 값이다. 위치를 모르면 그 항목은 생략한다.
            - 약 정보가 전혀 없는 이미지면 {"medications": []} 를 반환한다.
            - 설명이나 마크다운 없이 JSON 객체만 출력한다.
            형식: {"document_type":"","document_box_2d":[0,0,0,0],"medications":[{"drug_name":"","custom_drug_name":"","category":"","strength":"","effect":"","intake_reason":"","storage_location":null,"frequency":"","dose_amount":"","interval_days":1,"intake_timing":"","meal_time_slots":[],"confidence":0,"regions":[{"field":"DRUG_NAME","box_2d":[0,0,0,0]}]}]}
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
            raw = aiFileClient.askWithFile(SYSTEM_PROMPT, mediaType, file.getBytes(), "이 파일에서 복약 정보를 추출해줘.");
        } catch (Exception e) {
            log.error("AI 인식 호출 실패", e);
            throw new CustomException(MedicationErrorCode.AI_RECOGNITION_FAILED);
        }

        AiResult result;
        try {
            String json = raw.strip()
                    .replaceAll("^```(?:json)?\\s*", "")
                    .replaceAll("\\s*```$", "");
            result = jsonMapper.readValue(json, AiResult.class);
        } catch (Exception e) {
            log.error("AI 응답 파싱 실패: {}", raw, e);
            throw new CustomException(MedicationErrorCode.AI_RESPONSE_INVALID);
        }

        boolean withRegions = !"application/pdf".equals(mediaType); // PDF는 사진 위 박스를 그릴 수 없어 좌표 제외
        List<RecognitionResponse.RecognizedMedication> medications =
                result == null || result.medications() == null ? List.of() : result.medications().stream()
                        .filter(Objects::nonNull)
                        .map(m -> toMedication(m, withRegions))
                        .filter(Objects::nonNull)
                        .toList();
        if (medications.isEmpty()) {
            throw new CustomException(MedicationErrorCode.NO_MEDICATION_RECOGNIZED);
        }

        return new RecognitionResponse(
                safeFileName(file.getOriginalFilename()),
                clean(result.documentType(), 30),
                withRegions ? toRegion("DOCUMENT_TYPE", result.documentBox()) : null,
                overallConfidence(medications),
                medications);
    }

    // 약 이름이 없는 항목은 등록할 수 없어 제외
    private RecognitionResponse.RecognizedMedication toMedication(AiMedication m, boolean withRegions) {
        String drugName = clean(m.drugName(), 100);
        if (drugName == null) {
            return null;
        }
        String frequency = clean(m.frequency(), 100);
        IntakeTiming intakeTiming = enumOrNull(IntakeTiming.class, m.intakeTiming());

        // 문서에 적힌 끼니를 우선 쓰고(한글 응답도 허용), 없으면 하루 복용 횟수로 기본 끼니를 채운다
        List<MealTimeSlot> slots = m.mealTimeSlots() == null ? List.of() : m.mealTimeSlots().stream()
                .map(MealTimeSlot::from)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (slots.isEmpty()) {
            slots = defaultSlots(frequency, intakeTiming);
        }

        Integer interval = m.intervalDays() != null && m.intervalDays() >= 1 && m.intervalDays() <= 30
                ? m.intervalDays() : null;
        Integer confidence = m.confidence() == null ? null : Math.max(0, Math.min(100, m.confidence()));

        return new RecognitionResponse.RecognizedMedication(
                drugName,
                clean(m.customDrugName(), 100),
                clean(m.category(), 50),
                clean(m.strength(), 20),
                clean(m.effect(), 1000),
                clean(m.intakeReason(), 1000),
                clean(m.storageLocation(), 100),
                frequency,
                clean(m.doseAmount(), 20),
                interval,
                intakeTiming,
                slots,
                confidence,
                toRegions(m.regions(), withRegions));
    }

    // 끼니가 문서에 없을 때 쓰는 기본값: 1회 아침(취침 전 복용이면 자기 전), 2회 아침·저녁, 3회 아침·점심·저녁, 4회 + 자기 전
    private static List<MealTimeSlot> defaultSlots(String frequency, IntakeTiming intakeTiming) {
        boolean beforeBed = intakeTiming == IntakeTiming.BEFORE_BED;
        Integer times = timesPerDay(frequency);
        if (times == null) {
            return beforeBed ? List.of(MealTimeSlot.BEFORE_SLEEP) : List.of();
        }
        return switch (times) {
            case 1 -> beforeBed ? List.of(MealTimeSlot.BEFORE_SLEEP) : List.of(MealTimeSlot.MORNING);
            case 2 -> List.of(MealTimeSlot.MORNING, MealTimeSlot.DINNER);
            case 3 -> List.of(MealTimeSlot.MORNING, MealTimeSlot.LUNCH, MealTimeSlot.DINNER);
            case 4 -> List.of(MealTimeSlot.MORNING, MealTimeSlot.LUNCH, MealTimeSlot.DINNER, MealTimeSlot.BEFORE_SLEEP);
            default -> List.of();
        };
    }

    // frequency 문장에서 하루 복용 횟수를 찾는다. 못 찾으면 null
    private static Integer timesPerDay(String frequency) {
        if (frequency == null) {
            return null;
        }
        Matcher matcher = TIMES_PER_DAY.matcher(frequency);
        if (!matcher.find()) {
            return null;
        }
        String count = matcher.group(1);
        return switch (count) {
            case "한" -> 1;
            case "두" -> 2;
            case "세" -> 3;
            case "네" -> 4;
            default -> {
                try {
                    yield Integer.parseInt(count);
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
        };
    }

    // 허용한 필드만, 필드당 하나. 잘못된 박스는 버리고 다음 후보를 쓴다
    private static List<RecognitionResponse.Region> toRegions(List<AiRegion> regions, boolean withRegions) {
        if (!withRegions || regions == null) {
            return List.of();
        }
        Map<String, RecognitionResponse.Region> byField = new LinkedHashMap<>();
        for (AiRegion r : regions) {
            if (r == null || r.field() == null) {
                continue;
            }
            String field = r.field().strip().toUpperCase(Locale.ROOT);
            if (!REGION_FIELDS.contains(field) || byField.containsKey(field)) {
                continue;
            }
            RecognitionResponse.Region region = toRegion(field, r.box());
            if (region != null) {
                byField.put(field, region);
            }
        }
        return List.copyOf(byField.values());
    }

    // [ymin, xmin, ymax, xmax] (0~1000) → 0~1 비율의 x, y, width, height
    private static RecognitionResponse.Region toRegion(String field, List<Integer> box) {
        if (box == null || box.size() != 4 || box.stream().anyMatch(v -> v == null || v < 0 || v > 1000)) {
            return null;
        }
        int ymin = box.get(0), xmin = box.get(1), ymax = box.get(2), xmax = box.get(3);
        if (ymax <= ymin || xmax <= xmin) {
            return null;
        }
        return new RecognitionResponse.Region(field, round4(xmin / 1000.0), round4(ymin / 1000.0),
                round4((xmax - xmin) / 1000.0), round4((ymax - ymin) / 1000.0));
    }

    private static Integer overallConfidence(List<RecognitionResponse.RecognizedMedication> medications) {
        OptionalDouble average = medications.stream()
                .map(RecognitionResponse.RecognizedMedication::confidence)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average();
        return average.isPresent() ? (int) Math.round(average.getAsDouble()) : null;
    }

    // 공백·"null" 문자열은 null, 길이는 저장 한도에 맞게 자른다 (등록 요청의 검증에 걸리지 않도록)
    private static String clean(String value, int max) {
        if (value == null) {
            return null;
        }
        String text = value.strip();
        if (text.isEmpty() || text.equalsIgnoreCase("null")) {
            return null;
        }
        return text.length() > max ? text.substring(0, max) : text;
    }

    private static <E extends Enum<E>> E enumOrNull(Class<E> type, String value) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static double round4(double value) {
        return Math.round(value * 10_000) / 10_000.0;
    }

    // 경로·제어문자를 제거하고 255자로 제한. 클라이언트가 보낸 파일명을 그대로 믿지 않는다
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

    // AI 응답을 있는 그대로 받는 내부 모델. enum 필드도 문자열로 받아 잘못된 값에 전체가 실패하지 않게 한다
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiResult(
            @JsonProperty("document_type") String documentType,
            @JsonProperty("document_box_2d") List<Integer> documentBox,
            List<AiMedication> medications) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiMedication(
            @JsonProperty("drug_name") String drugName,
            @JsonProperty("custom_drug_name") String customDrugName,
            String category,
            String strength,
            String effect,
            @JsonProperty("intake_reason") String intakeReason,
            @JsonProperty("storage_location") String storageLocation,
            String frequency,
            @JsonProperty("dose_amount") String doseAmount,
            @JsonProperty("interval_days") Integer intervalDays,
            @JsonProperty("intake_timing") String intakeTiming,
            @JsonProperty("meal_time_slots") List<String> mealTimeSlots,
            Integer confidence,
            List<AiRegion> regions) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiRegion(String field, @JsonProperty("box_2d") List<Integer> box) {}
}