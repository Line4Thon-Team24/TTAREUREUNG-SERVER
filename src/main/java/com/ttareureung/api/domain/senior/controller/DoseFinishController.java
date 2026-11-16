package com.ttareureung.api.domain.senior.controller;

import com.ttareureung.api.domain.senior.dto.FinishResponse;
import com.ttareureung.api.domain.senior.dto.HelpResponse;
import com.ttareureung.api.domain.senior.service.DoseFinishService;
import com.ttareureung.api.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회차 종료(결과 화면)와 도움 요청 API
 */
@RestController
@RequestMapping("/api/v1/dose-sessions/{sessionId}")
@Tag(name = "DoseFinish", description = "시니어 회차 종료·도움 요청")
public class DoseFinishController {

    private final DoseFinishService finishService;

    public DoseFinishController(DoseFinishService finishService) {
        this.finishService = finishService;
    }

    @Operation(summary = "회차 종료", description = "약을 다 처리한 뒤 호출합니다. 전부 복용 → COMPLETED, 일부 → PARTIAL(못 먹은 약은 일정 하루 연장), 하나도 안 먹음 → 10분 뒤 재전화 대기(SCHEDULED), 3회째면 MISSED. 결과에 맞는 보호자 알림을 만듭니다.")
    @ApiResponse(responseCode = "200", description = "종료 처리 완료")
    @ApiResponse(responseCode = "403", description = "비활성 계정 (AUTH_005)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "404", description = "없는 회차 또는 접근 권한 없는 회차 (SENIOR_003)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "409", description = "이미 끝난 회차 (SENIOR_004), 전화 받기 전 (SENIOR_008)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @PostMapping("/finish")
    public ResponseEntity<BaseResponse<FinishResponse>> finish(@AuthenticationPrincipal Jwt jwt,
                                                               @PathVariable Long sessionId) {
        return ResponseEntity.ok(BaseResponse.success(finishService.finish(accountId(jwt), sessionId)));
    }

    @Operation(summary = "도움 요청", description = "약을 못 찾거나 인식이 안 될 때 호출합니다. 보호자 알림(HELP_REQUEST)을 만듭니다.")
    @ApiResponse(responseCode = "200", description = "요청 완료")
    @ApiResponse(responseCode = "403", description = "비활성 계정 (AUTH_005)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "404", description = "없는 회차 또는 접근 권한 없는 회차 (SENIOR_003)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "409", description = "이미 끝난 회차 (SENIOR_004)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @PostMapping("/help")
    public ResponseEntity<BaseResponse<HelpResponse>> help(@AuthenticationPrincipal Jwt jwt,
                                                           @PathVariable Long sessionId) {
        return ResponseEntity.ok(BaseResponse.success(finishService.help(accountId(jwt), sessionId)));
    }

    private Long accountId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
