package com.ttareureung.api.domain.senior.controller;

import com.ttareureung.api.domain.senior.dto.CurrentSessionResponse;
import com.ttareureung.api.domain.senior.dto.NoAnswerResponse;
import com.ttareureung.api.domain.senior.dto.SessionCallResponse;
import com.ttareureung.api.domain.senior.service.DoseCallService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 복약 전화 흐름 API.
 * 음성 합성·인식은 프론트(Web Speech API) 담당, 서버는 읽어 줄 문장(voiceText)만 제공
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "DoseCall", description = "시니어 복약 전화 수신·응답")
public class DoseCallController {

    private final DoseCallService callService;

    public DoseCallController(DoseCallService callService) {
        this.callService = callService;
    }

    @Operation(summary = "지금 울려야 할 회차", description = "프론트가 앱이 켜진 동안 1분 간격으로 호출합니다. current가 있으면 수신화면을 띄우고, 전화 받기 시 answer를 호출합니다. 오늘 회차가 없으면 생성합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공. current·next는 없으면 null")
    @ApiResponse(responseCode = "400", description = "시니어 프로필이 아님 (MEDICATION_003)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "404", description = "없는 프로필 또는 접근 권한 없는 프로필 (PROFILE_002)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @GetMapping("/seniors/{seniorId}/dose-sessions/current")
    public ResponseEntity<BaseResponse<CurrentSessionResponse>> current(@AuthenticationPrincipal Jwt jwt,
                                                                        @PathVariable Long seniorId) {
        return ResponseEntity.ok(BaseResponse.success(callService.current(accountId(jwt), seniorId)));
    }

    @Operation(summary = "전화 받기", description = "회차를 진행 중으로 바꾸고 아직 안 먹은 약 목록을 돌려줍니다. 이미 받은 회차에 다시 호출해도 같은 목록을 반환합니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "403", description = "비활성 계정 (AUTH_005)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "404", description = "없는 회차 또는 접근 권한 없는 회차 (SENIOR_003)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "409", description = "이미 끝난 회차 (SENIOR_004)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @PostMapping("/dose-sessions/{sessionId}/answer")
    public ResponseEntity<BaseResponse<SessionCallResponse>> answer(@AuthenticationPrincipal Jwt jwt,
                                                                    @PathVariable Long sessionId) {
        return ResponseEntity.ok(BaseResponse.success(callService.answer(accountId(jwt), sessionId)));
    }

    @Operation(summary = "전화 미응답", description = "수신화면에서 받지 않고 닫힌 경우 호출합니다. 10분 뒤로 재전화를 예약하고, 3회째면 회차를 미복용으로 확정합니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "403", description = "비활성 계정 (AUTH_005)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "404", description = "없는 회차 또는 접근 권한 없는 회차 (SENIOR_003)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "409", description = "이미 끝난 회차 (SENIOR_004), 전화 받은 뒤 호출 (SENIOR_005)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @PostMapping("/dose-sessions/{sessionId}/no-answer")
    public ResponseEntity<BaseResponse<NoAnswerResponse>> noAnswer(@AuthenticationPrincipal Jwt jwt,
                                                                   @PathVariable Long sessionId) {
        return ResponseEntity.ok(BaseResponse.success(callService.noAnswer(accountId(jwt), sessionId)));
    }

    private Long accountId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
