package com.ttareureung.api.domain.senior.controller;

import com.ttareureung.api.domain.senior.dto.DoseTimesResponse;
import com.ttareureung.api.domain.senior.dto.DoseTimesUpdateRequest;
import com.ttareureung.api.domain.senior.dto.ScheduleResponse;
import com.ttareureung.api.domain.senior.service.DoseScheduleService;
import com.ttareureung.api.domain.senior.service.DoseTimeService;
import com.ttareureung.api.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 시니어 복용 시각·일정 API.
 * seniorId는 내 계정의 시니어이거나 내 계정 보호자와 연결된 시니어여야 함
 */
@RestController
@RequestMapping("/api/v1/seniors/{seniorId}")
@Tag(name = "SeniorSchedule", description = "시니어 복용 시각과 날짜별 일정")
public class SeniorScheduleController {

    private final DoseTimeService doseTimeService;
    private final DoseScheduleService scheduleService;

    public SeniorScheduleController(DoseTimeService doseTimeService, DoseScheduleService scheduleService) {
        this.doseTimeService = doseTimeService;
        this.scheduleService = scheduleService;
    }

    @Operation(summary = "회차 시각 조회", description = "아침·점심·저녁·밤 전화 시각입니다. 설정한 적이 없으면 기본값(08:00 / 12:30 / 18:30 / 22:00)을 반환합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "400", description = "시니어 프로필이 아님 (MEDICATION_003)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "404", description = "없는 프로필 또는 접근 권한 없는 프로필 (PROFILE_002)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @GetMapping("/dose-times")
    public ResponseEntity<BaseResponse<DoseTimesResponse>> getDoseTimes(@AuthenticationPrincipal Jwt jwt,
                                                                        @PathVariable Long seniorId) {
        return ResponseEntity.ok(BaseResponse.success(doseTimeService.get(accountId(jwt), seniorId)));
    }

    @Operation(summary = "회차 시각 변경", description = "보낸 회차만 변경합니다. 변경 후 전체 시각이 아침 < 점심 < 저녁 < 밤 순서여야 합니다. 오늘 회차 중 아직 전화 전인 것은 새 시각으로 바뀝니다.")
    @ApiResponse(responseCode = "200", description = "변경 성공")
    @ApiResponse(responseCode = "400", description = "입력값 오류, 회차 중복 (SENIOR_002), 순서 오류 (SENIOR_001), 시니어 프로필 아님 (MEDICATION_003)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "403", description = "비활성 계정 (AUTH_005)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "404", description = "없는 프로필 또는 접근 권한 없는 프로필 (PROFILE_002)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @PutMapping(value = "/dose-times", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BaseResponse<DoseTimesResponse>> updateDoseTimes(@AuthenticationPrincipal Jwt jwt,
                                                                           @PathVariable Long seniorId,
                                                                           @Valid @RequestBody DoseTimesUpdateRequest request) {
        return ResponseEntity.ok(BaseResponse.success(doseTimeService.update(accountId(jwt), seniorId, request)));
    }

    @Operation(summary = "날짜별 복용 일정", description = "date가 없으면 오늘(KST)입니다. 오늘 날짜를 조회하면 약이 있는 회차의 sessionId가 생기고, 다른 날짜는 sessionId가 null입니다. 약이 등록되지 않은 날은 sessions가 비어 있습니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "400", description = "날짜 형식 오류, 시니어 프로필 아님 (MEDICATION_003)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "404", description = "없는 프로필 또는 접근 권한 없는 프로필 (PROFILE_002)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @GetMapping("/schedules")
    public ResponseEntity<BaseResponse<ScheduleResponse>> getSchedule(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long seniorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(BaseResponse.success(scheduleService.getSchedule(accountId(jwt), seniorId, date)));
    }

    private Long accountId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
