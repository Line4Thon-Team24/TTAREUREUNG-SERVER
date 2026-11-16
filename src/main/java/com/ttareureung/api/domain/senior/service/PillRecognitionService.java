package com.ttareureung.api.domain.senior.service;

import com.ttareureung.api.domain.medication.client.AiFileClient;
import com.ttareureung.api.domain.medication.entity.MealTimeSlot;
import com.ttareureung.api.domain.medication.entity.Medication;
import com.ttareureung.api.domain.medication.exception.MedicationErrorCode;
import com.ttareureung.api.domain.profile.service.ProfileAccessChecker;
import com.ttareureung.api.domain.senior.dto.RecognitionResultResponse;
import com.ttareureung.api.domain.senior.entity.DoseSession;
import com.ttareureung.api.domain.senior.entity.MedicationRecognition;
import com.ttareureung.api.domain.senior.entity.RecognitionResult;
import com.ttareureung.api.domain.senior.exception.SeniorErrorCode;
import com.ttareureung.api.domain.senior.repository.MedicationRecognitionRepository;
import com.ttareureung.api.global.exception.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 시니어가 보여 준 약 사진이 복용해야 할 약인지 Gemini로 판독.
 * 모델 호출 중에는 DB 잠금을 잡지 않도록 트랜잭션 없이 진행, 저장만 리포지토리 트랜잭션
 */
@Slf4j
@Service
public class PillRecognitionService {

    static final int MAX_ATTEMPTS_PER_MEDICATION = 10;
    static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    // MATCH는 근거가 분명할 때만. 애매하면 UNCERTAIN으로 유도해 잘못된 복용 방지
    private static final String SYSTEM_PROMPT = """
            너는 시니어가 카메라로 보여 주는 약이 지금 복용해야 할 약과 같은지 판단하는 도구다.
            - 등록된 약 정보(이름, 생김새, 1회 복용량, 복용 시간대)와 사진 속 약(알약, 약 봉투, 약 상자)을 비교한다.
            - 약 봉투나 처방 봉투면 인쇄된 약 이름과 복용 시간대(아침/점심/저녁/취침 전)를 등록 정보와 비교한다.
            - 확실히 같으면 MATCH, 확실히 다르면 MISMATCH, 사진이 흐리거나 약이 안 보이거나 근거가 부족하면 UNCERTAIN.
            - 모르면 반드시 UNCERTAIN. MATCH는 근거가 분명할 때만 답한다.
            - 설명이나 마크다운 없이 JSON 객체만 출력한다.
            형식: {"result":"MATCH","reason":"한 문장 근거"} (result는 MATCH, MISMATCH, UNCERTAIN 중 하나)
            """;

    private final ProfileAccessChecker accessChecker;
    private final DoseSessionFinder finder;
    private final MedicationRecognitionRepository recognitions;
    private final AiFileClient aiFileClient;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public PillRecognitionService(ProfileAccessChecker accessChecker, DoseSessionFinder finder,
                                  MedicationRecognitionRepository recognitions, AiFileClient aiFileClient,
                                  JsonMapper jsonMapper, Clock clock) {
        this.accessChecker = accessChecker;
        this.finder = finder;
        this.recognitions = recognitions;
        this.aiFileClient = aiFileClient;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    public RecognitionResultResponse recognize(Long accountId, Long sessionId, Long medicationId, MultipartFile file) {
        accessChecker.activeAccount(accountId);
        validateImage(file);
        DoseSession session = finder.accessible(accountId, sessionId);
        finder.requireInProgress(session);
        Medication medication = finder.logOf(session, medicationId).getMedication();

        long attempts = recognitions.countBySessionIdAndMedicationId(sessionId, medicationId);
        if (attempts >= MAX_ATTEMPTS_PER_MEDICATION) {
            throw new CustomException(SeniorErrorCode.RECOGNITION_LIMIT_EXCEEDED);
        }

        RecognitionResult result;
        String reason;
        try {
            String raw = aiFileClient.askWithFile(SYSTEM_PROMPT, file.getContentType(), file.getBytes(),
                    userPrompt(medication, session.getTimeSlot()));
            JsonNode node = jsonMapper.readTree(stripFence(raw));
            result = parseResult(node.path("result").asString(""));
            reason = node.path("reason").asString("");
        } catch (Exception e) {
            // 키 미설정, 시간 초과, 응답 파싱 실패 모두 UNCERTAIN. 화면은 "사진 보고 직접 선택"으로 진행
            log.warn("약 판독 실패 session={} medication={}: {}", sessionId, medicationId, e.toString());
            result = RecognitionResult.UNCERTAIN;
            reason = "판독 실패: " + e.getClass().getSimpleName();
        }

        MedicationRecognition saved = recognitions.save(new MedicationRecognition(
                session, medicationId, result, reason, LocalDateTime.now(clock.withZone(SeniorTime.ZONE))));
        int attemptsLeft = (int) (MAX_ATTEMPTS_PER_MEDICATION - attempts - 1);
        return new RecognitionResultResponse(saved.getId(), medicationId, result,
                VoiceText.recognition(result), VoiceText.recognition(result), attemptsLeft);
    }

    private static void validateImage(MultipartFile file) {
        String type = file.getContentType();
        if (file.isEmpty() || type == null || !IMAGE_TYPES.contains(type)) {
            throw new CustomException(MedicationErrorCode.UNSUPPORTED_FILE_TYPE);
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new CustomException(SeniorErrorCode.IMAGE_TOO_LARGE);
        }
    }

    // 등록 정보를 글자로 전달. 등록 사진은 클라이언트가 파일 하나만 받아 생략
    private static String userPrompt(Medication m, MealTimeSlot slot) {
        return "등록된 약: 이름 \"" + m.getDisplayName() + "\" (원래 이름 \"" + m.getDrugName() + "\")"
                + ", 생김새 " + orUnknown(m.getAppearance())
                + ", 1회 복용량 " + orUnknown(m.getDoseAmount())
                + ", 복용 시간대 " + slot.getLabel()
                + ". 이 사진의 약이 등록된 약과 같은지 판단해줘.";
    }

    private static String orUnknown(String s) {
        return s == null || s.isBlank() ? "모름" : "\"" + s + "\"";
    }

    private static String stripFence(String raw) {
        return raw.strip().replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
    }

    // 정의된 세 값 외에는 전부 UNCERTAIN
    private static RecognitionResult parseResult(String value) {
        try {
            return RecognitionResult.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return RecognitionResult.UNCERTAIN;
        }
    }
}
