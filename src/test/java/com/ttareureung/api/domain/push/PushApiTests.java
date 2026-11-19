package com.ttareureung.api.domain.push;

import com.ttareureung.api.domain.push.client.PushGateway;
import com.ttareureung.api.domain.push.client.WebPushCrypto;
import com.ttareureung.api.domain.push.service.DosePushJob;
import com.ttareureung.api.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.security.interfaces.ECPublicKey;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PushApiTests extends ApiTestSupport {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String D = LocalDate.now(KST).toString();

    @MockitoBean
    private PushGateway gateway;

    @Autowired
    private DosePushJob job;

    @BeforeEach
    void gatewayOn() {
        when(gateway.enabled()).thenReturn(true);
        when(gateway.send(anyString(), anyString(), anyString(), any())).thenReturn(201);
    }

    @Test
    void subscribeUpsertsByEndpointAndUnsubscribeRemoves() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        String endpoint = endpoint();

        subscribe(token, f.guardianId(), endpoint)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.subscriptionId").isNumber())
                .andExpect(jsonPath("$.data.profileId").value(f.guardianId()));
        // 같은 기기를 시니어 프로필로 다시 등록하면 이동
        subscribe(token, f.seniorId(), endpoint).andExpect(status().isCreated());
        assertThat(count("SELECT COUNT(*) FROM push_subscriptions WHERE endpoint = ?", endpoint)).isEqualTo(1);
        assertThat(count("SELECT profile_id FROM push_subscriptions WHERE endpoint = ?", endpoint)).isEqualTo(f.seniorId());

        // snake_case profile_id도 허용
        Map<String, Object> snake = body(null, endpoint());
        snake.put("profile_id", f.guardianId());
        postJson(token, "/api/v1/push-subscriptions", snake).andExpect(status().isCreated());

        unsubscribe(bearer(newAccount()), endpoint).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PUSH_001"));
        unsubscribe(token, endpoint).andExpect(status().isOk());
        assertThat(count("SELECT COUNT(*) FROM push_subscriptions WHERE endpoint = ?", endpoint)).isZero();
    }

    @Test
    void rejectsInvalidSubscriptions() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        Map<String, Object> badKeys = body(f.guardianId(), endpoint());
        badKeys.put("keys", Map.of("p256dh", "not-a-key", "auth", "abc"));

        postJson(token, "/api/v1/push-subscriptions", badKeys).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PUSH_002"));
        postJson(token, "/api/v1/push-subscriptions", body(f.guardianId(), "http://insecure.example.com/x"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON_400"));
        subscribe(bearer(newAccount()), f.guardianId(), endpoint()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_002"));
        getAs(token, "/api/v1/push-subscriptions/vapid-public-key").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));
    }

    @Test
    void guardianAlertsArePushedToLinkedGuardiansRespectingSettings() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        String guardianEndpoint = endpoint();
        subscribe(token, f.guardianId(), guardianEndpoint).andExpect(status().isCreated());
        long sessionId = answeredMorningSession(f, token);

        postJson(token, "/api/v1/dose-sessions/" + sessionId + "/help", "").andExpect(status().isOk());
        String payload = captured(guardianEndpoint);
        assertThat(payload).contains("\"type\":\"GUARDIAN_ALERT\"").contains("\"alertType\":\"HELP_REQUEST\"")
                .contains("\"alertId\":").contains("\"sessionId\":" + sessionId).contains("도움을 요청했어요");

        // 완료 알림 OFF면 완료 푸시는 보내지 않음
        patchJson(token, "/api/v1/notification-settings", Map.of("profile_id", f.guardianId(), "notify_on_complete", false))
                .andExpect(status().isOk());
        long medicationId = jdbcTemplate.queryForObject(
                "SELECT medication_id FROM medication_logs ml JOIN medication m ON ml.medication_id = m.id WHERE m.senior_profile_id = ? LIMIT 1",
                Long.class, f.seniorId());
        postJson(token, "/api/v1/dose-sessions/" + sessionId + "/medications/" + medicationId + "/intake", "").andExpect(status().isOk());
        postJson(token, "/api/v1/dose-sessions/" + sessionId + "/finish", "").andExpect(jsonPath("$.data.status").value("COMPLETED"));
        verify(gateway, after(500).times(1)).send(eq(guardianEndpoint), anyString(), anyString(), any());
    }

    @Test
    void jobCreatesSessionAndPushesCallOnceAndUpcomingToGuardian() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        registerMedication(f.accountId(), f.seniorId(), "허리약", D, D, "MORNING", "LUNCH");
        String seniorEndpoint = endpoint();
        String guardianEndpoint = endpoint();
        subscribe(token, f.seniorId(), seniorEndpoint).andExpect(status().isCreated());
        subscribe(token, f.guardianId(), guardianEndpoint).andExpect(status().isCreated());
        putJson(token, "/api/v1/seniors/" + f.seniorId() + "/dose-times", Map.of("doseTimes", List.of(
                Map.of("timeSlot", "MORNING", "doseTime", "00:00"), Map.of("timeSlot", "LUNCH", "doseTime", "23:57"),
                Map.of("timeSlot", "DINNER", "doseTime", "23:58"), Map.of("timeSlot", "BEFORE_SLEEP", "doseTime", "23:59"))))
                .andExpect(status().isOk());

        // 앱 조회 없이도 회차 생성 + 아침 전화 푸시 (전화 시각 00:00, 30분 유예 안의 00:05로 실행)
        LocalDateTime now = LocalDate.now(KST).atTime(0, 5);
        job.runOnce(now.minusMinutes(1), now);
        assertThat(count("SELECT COUNT(*) FROM dose_sessions WHERE senior_profile_id = ?", f.seniorId())).isEqualTo(2);
        String call = captured(seniorEndpoint);
        assertThat(call).contains("\"type\":\"DOSE_CALL\"").contains("\"sessionId\":").contains("아침 약 드실 시간이에요. 약이 1개 있어요.");

        // 같은 시각으로는 다시 보내지 않음
        job.runOnce(now, now.plusSeconds(30));
        verify(gateway, after(500).times(1)).send(eq(seniorEndpoint), anyString(), anyString(), any());
        // 30분이 지난 회차는 서버가 늦게 켜져도 울리지 않음
        assertThat(count("SELECT COUNT(*) FROM dose_sessions WHERE senior_profile_id = ? AND call_pushed_at IS NOT NULL", f.seniorId()))
                .isEqualTo(1);

        // 점심 23:57, 보호자 기본 10분 전 → 23:47이 이번 구간에 들어오면 임박 푸시
        LocalDateTime lunch = LocalDate.now(KST).atTime(23, 57);
        job.runOnce(lunch.minusMinutes(11), lunch.minusMinutes(10));
        String upcoming = captured(guardianEndpoint);
        assertThat(upcoming).contains("\"type\":\"DOSE_UPCOMING\"").contains("10분 후");
    }

    @Test
    void expiredSubscriptionIsDeletedAndProfileDeletionClearsSubscriptions() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        String endpoint = endpoint();
        subscribe(token, f.guardianId(), endpoint).andExpect(status().isCreated());
        when(gateway.send(eq(endpoint), anyString(), anyString(), any())).thenReturn(410);
        long sessionId = answeredMorningSession(f, token);

        postJson(token, "/api/v1/dose-sessions/" + sessionId + "/help", "").andExpect(status().isOk());
        verify(gateway, timeout(2000)).send(eq(endpoint), anyString(), anyString(), any());
        for (int i = 0; i < 20 && count("SELECT COUNT(*) FROM push_subscriptions WHERE endpoint = ?", endpoint) > 0; i++) {
            Thread.sleep(100);
        }
        assertThat(count("SELECT COUNT(*) FROM push_subscriptions WHERE endpoint = ?", endpoint)).isZero();

        String seniorEndpoint = endpoint();
        subscribe(token, f.seniorId(), seniorEndpoint).andExpect(status().isCreated());
        deleteAs(token, "/api/v1/profiles/" + f.seniorId()).andExpect(status().is2xxSuccessful());
        assertThat(count("SELECT COUNT(*) FROM push_subscriptions WHERE endpoint = ?", seniorEndpoint)).isZero();
    }

    @Test
    void noPushWhenGatewayDisabled() throws Exception {
        when(gateway.enabled()).thenReturn(false);
        Family f = newFamily();
        String token = bearer(f.accountId());
        subscribe(token, f.guardianId(), endpoint()).andExpect(status().isCreated());
        long sessionId = answeredMorningSession(f, token);

        postJson(token, "/api/v1/dose-sessions/" + sessionId + "/help", "").andExpect(status().isOk());
        verify(gateway, after(300).never()).send(anyString(), anyString(), anyString(), any());
        getAs(token, "/api/v1/push-subscriptions/vapid-public-key")
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.publicKey").doesNotExist());
    }

    private long answeredMorningSession(Family f, String token) throws Exception {
        registerMedication(f.accountId(), f.seniorId(), "허리약", D, D, "MORNING");
        putJson(token, "/api/v1/seniors/" + f.seniorId() + "/dose-times", Map.of("doseTimes", List.of(
                Map.of("timeSlot", "MORNING", "doseTime", "00:00"), Map.of("timeSlot", "LUNCH", "doseTime", "00:01"),
                Map.of("timeSlot", "DINNER", "doseTime", "00:02"), Map.of("timeSlot", "BEFORE_SLEEP", "doseTime", "00:03"))))
                .andExpect(status().isOk());
        long sessionId = jsonMapper.readTree(getAs(token, "/api/v1/seniors/" + f.seniorId() + "/dose-sessions/current")
                .andReturn().getResponse().getContentAsString()).path("data").path("current").path("sessionId").asLong();
        postJson(token, "/api/v1/dose-sessions/" + sessionId + "/answer", "").andExpect(status().isOk());
        return sessionId;
    }

    private String captured(String endpoint) {
        ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);
        verify(gateway, timeout(2000).atLeastOnce()).send(eq(endpoint), anyString(), anyString(), payload.capture());
        return new String(payload.getValue(), StandardCharsets.UTF_8);
    }

    private ResultActions subscribe(String token, long profileId, String endpoint) throws Exception {
        return postJson(token, "/api/v1/push-subscriptions", body(profileId, endpoint));
    }

    private ResultActions unsubscribe(String token, String endpoint) throws Exception {
        return mockMvc.perform(delete("/api/v1/push-subscriptions").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"" + endpoint + "\"}"));
    }

    private static Map<String, Object> body(Long profileId, String endpoint) throws Exception {
        byte[] p256dh = WebPushCrypto.toUncompressed((ECPublicKey) WebPushCrypto.newKeyPair().getPublic());
        var map = new java.util.LinkedHashMap<String, Object>();
        if (profileId != null) {
            map.put("profileId", profileId);
        }
        map.put("endpoint", endpoint);
        map.put("keys", Map.of("p256dh", WebPushCrypto.encode(p256dh), "auth", WebPushCrypto.encode(new byte[16])));
        return map;
    }

    private static String endpoint() {
        return "https://push.example.com/send/" + UUID.randomUUID();
    }
}
