package com.ttareureung.api.domain.push.controller;

import com.ttareureung.api.domain.push.dto.PushDtos;
import com.ttareureung.api.domain.push.service.PushSubscriptionService;
import com.ttareureung.api.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 웹 푸시(VAPID) 구독 API.
 * 보호자: 알림(완료·일부·미복용·도움 요청)과 복용 임박, 시니어: 복용 시각 전화 알림을 받음
 */
@RestController
@RequestMapping("/api/v1/push-subscriptions")
@Tag(name = "Push", description = "웹 푸시 구독")
public class PushSubscriptionController {

    private final PushSubscriptionService service;

    public PushSubscriptionController(PushSubscriptionService service) {
        this.service = service;
    }

    @Operation(summary = "VAPID 공개키 조회", description = "브라우저 pushManager.subscribe의 applicationServerKey로 사용합니다. enabled가 false면 서버에 키가 없어 푸시가 발송되지 않습니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/vapid-public-key")
    public ResponseEntity<BaseResponse<PushDtos.VapidKeyResponse>> vapidKey() {
        return ResponseEntity.ok(BaseResponse.success(service.vapidKey()));
    }

    @Operation(summary = "푸시 기기 등록", description = "PushSubscription.toJSON()의 endpoint·keys에 profileId를 더해 보냅니다. 같은 기기를 다시 등록하면 갱신되고, 다른 프로필로 등록하면 그 프로필로 옮겨집니다.")
    @ApiResponse(responseCode = "201", description = "등록 성공")
    @ApiResponse(responseCode = "400", description = "입력값 오류, 구독 키 형식 오류 (PUSH_002)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "403", description = "비활성 계정 (AUTH_005)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "404", description = "없는 프로필 또는 다른 계정의 프로필 (PROFILE_002)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BaseResponse<PushDtos.SubscribeResponse>> subscribe(@AuthenticationPrincipal Jwt jwt,
                                                                              @Valid @RequestBody PushDtos.SubscribeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success(service.subscribe(accountId(jwt), request)));
    }

    @Operation(summary = "푸시 기기 해제", description = "로그아웃하거나 알림을 끌 때 호출합니다.")
    @ApiResponse(responseCode = "200", description = "해제 성공")
    @ApiResponse(responseCode = "404", description = "없는 구독 또는 다른 계정의 구독 (PUSH_001)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @DeleteMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BaseResponse<Void>> unsubscribe(@AuthenticationPrincipal Jwt jwt,
                                                          @Valid @RequestBody PushDtos.UnsubscribeRequest request) {
        service.unsubscribe(accountId(jwt), request);
        return ResponseEntity.ok(BaseResponse.successWithoutData());
    }

    private Long accountId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
