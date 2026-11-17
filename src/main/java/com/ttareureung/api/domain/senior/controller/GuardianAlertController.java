package com.ttareureung.api.domain.senior.controller;

import com.ttareureung.api.domain.senior.dto.GuardianAlertListResponse;
import com.ttareureung.api.domain.senior.dto.GuardianAlertResponse;
import com.ttareureung.api.domain.senior.service.GuardianAlertService;
import com.ttareureung.api.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 보호자 화면 알림 API. 푸시 없이 화면이 주기적으로 조회
 */
@RestController
@RequestMapping("/api/v1/guardian-alerts")
@Tag(name = "GuardianAlert", description = "보호자 알림")
public class GuardianAlertController {

    private final GuardianAlertService alertService;

    public GuardianAlertController(GuardianAlertService alertService) {
        this.alertService = alertService;
    }

    @Operation(summary = "알림 목록", description = "내 계정 시니어(연결된 다른 계정 시니어 포함)의 알림을 최신순으로 반환합니다. profileId(내 보호자 프로필)를 주면 그 프로필의 알림 설정(완료·미복용 ON/OFF, 임박 N분)을 적용하고, upcoming에 N분 안에 울릴 회차를 넣습니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "profileId가 없는 프로필 또는 다른 계정의 프로필 (PROFILE_002)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @GetMapping
    public ResponseEntity<BaseResponse<GuardianAlertListResponse>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long profileId,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return ResponseEntity.ok(BaseResponse.success(alertService.list(accountId(jwt), profileId, unreadOnly)));
    }

    @Operation(summary = "알림 읽음", description = "읽은 시각을 기록합니다. 이미 읽은 알림은 처음 읽은 시각을 유지합니다. 보호자가 여러 명이어도 읽음은 알림당 하나입니다.")
    @ApiResponse(responseCode = "200", description = "처리 완료")
    @ApiResponse(responseCode = "403", description = "비활성 계정 (AUTH_005)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "404", description = "없는 알림 또는 접근 권한 없는 알림 (SENIOR_010)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @PatchMapping("/{alertId}/read")
    public ResponseEntity<BaseResponse<GuardianAlertResponse>> read(@AuthenticationPrincipal Jwt jwt,
                                                                    @PathVariable Long alertId) {
        return ResponseEntity.ok(BaseResponse.success(alertService.markRead(accountId(jwt), alertId)));
    }

    private Long accountId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
