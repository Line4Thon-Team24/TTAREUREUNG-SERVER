package com.ttareureung.api.domain.notification.controller;

import com.ttareureung.api.domain.notification.dto.NotificationSettingResponse;
import com.ttareureung.api.domain.notification.dto.NotificationSettingUpdateRequest;
import com.ttareureung.api.domain.notification.service.NotificationSettingService;
import com.ttareureung.api.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notification-settings")
@Tag(name = "NotificationSetting", description = "프로필 알림 설정")
public class NotificationSettingController {

    private final NotificationSettingService service;

    public NotificationSettingController(NotificationSettingService service) {
        this.service = service;
    }

    @Operation(summary = "알림 설정 조회", description = "저장된 설정이 없으면 기본값을 반환합니다. 내 계정의 프로필만 조회할 수 있습니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "없는 프로필 또는 다른 계정의 프로필 (PROFILE_002)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @GetMapping
    public ResponseEntity<BaseResponse<NotificationSettingResponse>> get(
            @AuthenticationPrincipal Jwt jwt, @RequestParam("profile_id") Long profileId) {
        return ResponseEntity.ok(BaseResponse.success(service.get(accountId(jwt), profileId)));
    }

    @Operation(summary = "알림 설정 변경", description = "보낸 항목만 변경합니다. remind_before_minutes는 0(끔)~180입니다.")
    @ApiResponse(responseCode = "200", description = "변경 성공")
    @ApiResponse(responseCode = "400", description = "입력값 오류", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "403", description = "비활성 계정 (AUTH_005)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "404", description = "없는 프로필 또는 다른 계정의 프로필 (PROFILE_002)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @PatchMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BaseResponse<NotificationSettingResponse>> update(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody NotificationSettingUpdateRequest request) {
        return ResponseEntity.ok(BaseResponse.success(service.update(accountId(jwt), request)));
    }

    private Long accountId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
