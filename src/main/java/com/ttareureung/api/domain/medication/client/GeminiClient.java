package com.ttareureung.api.domain.medication.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Component
public class GeminiClient implements AiFileClient {

    // 일시적인 오류로 보고 다시 시도하는 상태 코드: 429(한도 초과), 5xx(Gemini 서버 오류·과부하)
    private static final Set<Integer> RETRYABLE_STATUS = Set.of(429, 500, 502, 503, 504);
    private static final int MAX_RETRIES = 2;            // 첫 시도 + 재시도 2번 = 모델당 최대 3번
    private static final long BASE_BACKOFF_MS = 1000;    // 1초, 2초 순으로 기다림
    private static final long MAX_BACKOFF_MS = 5000;     // Retry-After가 길어도 최대 5초만 기다림

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final String fallbackModel;

    public GeminiClient(@Value("${gemini.api-key:}") String apiKey,
                        @Value("${gemini.model}") String model,
                        @Value("${gemini.fallback-model:}") String fallbackModel) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(60));

        this.apiKey = apiKey;
        this.model = model;
        this.fallbackModel = fallbackModel;
        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .requestFactory(factory)
                .defaultHeader("x-goog-api-key", apiKey)
                .build();
    }

    @Override
    public String askWithFile(String system, String mediaType, byte[] data, String prompt) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY가 설정되지 않았습니다.");
        }
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", system))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(
                        Map.of("inline_data", Map.of(
                                "mime_type", mediaType,
                                "data", Base64.getEncoder().encodeToString(data))),
                        Map.of("text", prompt)))),
                "generationConfig", Map.of("responseMimeType", "application/json", "temperature", 0));

        // 기본 모델로 재시도해도 안 되면 예비 모델로 한 번 더 시도 (같은 모델이면 생략)
        List<String> models = new ArrayList<>(List.of(model));
        if (fallbackModel != null && !fallbackModel.isBlank() && !fallbackModel.equals(model)) {
            models.add(fallbackModel);
        }

        RestClientResponseException lastError = null;
        for (int i = 0; i < models.size(); i++) {
            String targetModel = models.get(i);
            for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
                try {
                    return call(targetModel, body);
                } catch (RestClientResponseException e) {
                    int status = e.getStatusCode().value();
                    if (!RETRYABLE_STATUS.contains(status)) {
                        throw e; // 400·403·404 등은 다시 시도해도 같으므로 바로 실패
                    }
                    lastError = e;
                    log.warn("Gemini 일시 오류 {} (model={}, 시도 {}/{})",
                            status, targetModel, attempt + 1, MAX_RETRIES + 1);
                    if (attempt < MAX_RETRIES) {
                        sleep(backoffMillis(attempt, e));
                    }
                }
            }
            if (i < models.size() - 1) {
                log.warn("Gemini 모델 {} 재시도 실패, 예비 모델 {}로 전환", targetModel, models.get(i + 1));
            }
        }
        throw lastError;
    }

    private String call(String targetModel, Map<String, Object> body) {
        Response res = restClient.post()
                .uri("/v1beta/models/{model}:generateContent", targetModel)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Response.class);

        String text = Optional.ofNullable(res)
                .map(Response::candidates)
                .filter(c -> !c.isEmpty())
                .map(c -> c.get(0).content())
                .map(Content::parts)
                .orElse(List.of()).stream()
                .map(Part::text)
                .filter(Objects::nonNull)
                .collect(Collectors.joining());
        if (text.isBlank()) { // 텍스트가 없는 경우
            throw new IllegalStateException("Gemini 응답에 텍스트가 없습니다.");
        }
        return text;
    }

    // Retry-After(초)가 있으면 그 값을, 없으면 1초·2초에 약간의 랜덤을 더해 기다림
    private static long backoffMillis(int attempt, RestClientResponseException e) {
        HttpHeaders headers = e.getResponseHeaders();
        if (headers != null) {
            String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
            if (retryAfter != null) {
                try {
                    return Math.min(Long.parseLong(retryAfter.strip()) * 1000, MAX_BACKOFF_MS);
                } catch (NumberFormatException ignored) {
                    // 날짜 형식이면 기본 대기 시간 사용
                }
            }
        }
        long backoff = BASE_BACKOFF_MS * (1L << attempt);
        long jitter = ThreadLocalRandom.current().nextLong(0, 300);
        return Math.min(backoff + jitter, MAX_BACKOFF_MS);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gemini 재시도 대기 중 중단되었습니다.", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(List<Candidate> candidates) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Candidate(Content content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Content(List<Part> parts) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Part(String text) {}
}