package com.ttareureung.api.domain.medication.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ttareureung.api.domain.medication.entity.IntakeTiming;
import com.ttareureung.api.domain.medication.entity.MealTimeSlot;

import java.util.List;

public record RecognitionResponse(
        @JsonProperty("file_name") String fileName,                       // 업로드한 파일명
        @JsonProperty("document_type") String documentType,               // 문서 유형: 처방전, 약봉투 등
        @JsonProperty("document_region") Region documentRegion,           // 문서 유형 글자 위치 (PDF는 null)
        @JsonProperty("overall_confidence") Integer overallConfidence,    // 약별 confidence 평균
        List<RecognizedMedication> medications
) {
    public record RecognizedMedication(
            @JsonProperty("drug_name") String drugName,
            @JsonProperty("custom_drug_name") String customDrugName,      // 시니어가 알아볼 쉬운 이름
            String category,
            String strength,
            String effect,
            @JsonProperty("intake_reason") String intakeReason,
            @JsonProperty("storage_location") String storageLocation,     // 문서에 명시된 경우만, 대부분 null
            String frequency,
            @JsonProperty("dose_amount") String doseAmount,
            @JsonProperty("interval_days") Integer intervalDays,
            @JsonProperty("intake_timing") IntakeTiming intakeTiming,
            @JsonProperty("meal_time_slots") List<MealTimeSlot> mealTimeSlots,
            Integer confidence,                                           // 모델 추정값 (0~100)
            List<Region> regions                                          // DRUG_NAME / INTAKE_METHOD / FREQUENCY, PDF는 빈 배열
    ) {}

    /** 이미지 전체를 1로 본 비율. 왼쪽 위가 원점이고 (x, y)는 박스의 왼쪽 위 모서리 */
    public record Region(String field, double x, double y, double width, double height) {}
}
