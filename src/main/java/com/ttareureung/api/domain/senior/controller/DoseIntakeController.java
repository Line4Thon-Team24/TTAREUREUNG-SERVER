package com.ttareureung.api.domain.senior.controller;

import com.ttareureung.api.domain.senior.dto.IntakeResponse;
import com.ttareureung.api.domain.senior.dto.RecognitionResultResponse;
import com.ttareureung.api.domain.senior.service.IntakeService;
import com.ttareureung.api.domain.senior.service.PillRecognitionService;
import com.ttareureung.api.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 약 판독과 복용 처리 API. 전화를 받은(IN_PROGRESS) 회차에서만 동작
 * 촬영 이미지는 판독에만 쓰고 저장하지 않음
 */
@RestController
@RequestMapping("/api/v1/dose-sessions/{sessionId}/medications/{medicationId}")
@Tag(name = "DoseIntake", description = "시니어 약 판독·복용 처리")
public class DoseIntakeController {

    private final PillRecognitionService recognitionService;
    private final IntakeService intakeService;

    public DoseIntakeController(PillRecognitionService recognitionService, IntakeService intakeService) {
        this.recognitionService = recognitionService;
        this.intakeService = intakeService;
    }

    @Operation(summary = "약 판독", description = "카메라로 찍은 사진(file, JPG/PNG/WEBP, 5MB 이하)이 이 약인지 판독합니다. 확실하지 않으면 UNCERTAIN이며, 회차·약당 10회까지 가능합니다. MATCH여도 복용 처리는 intake를 따로 호출해야 합니다.")
    @ApiResponse(responseCode = "200", description = "판독 완료 (MATCH / MISMATCH / UNCERTAIN)")
    @ApiResponse(responseCode = "400", description = "file 누락", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "403", description = "비활성 계정 (AUTH_005)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "404", description = "없는 회차 (SENIOR_003), 이 회차에 없는 약 (SENIOR_006)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "409", description = "끝난 회차 (SENIOR_004), 전화 받기 전 (SENIOR_008)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "413", description = "사진 5MB 초과 (SENIOR_009)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "415", description = "이미지 파일 아님 (MEDICATION_007)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "429", description = "판독 횟수 초과 (SENIOR_007)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @PostMapping(value = "/recognitions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BaseResponse<RecognitionResultResponse>> recognize(@AuthenticationPrincipal Jwt jwt,
                                                                             @PathVariable Long sessionId,
                                                                             @PathVariable Long medicationId,
                                                                             @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(BaseResponse.success(
                recognitionService.recognize(accountId(jwt), sessionId, medicationId, file)));
    }

    @Operation(summary = "복용 처리", description = "\"다 먹었어\" 시 호출합니다. 직전 판독이 MATCH면 VISION, 아니면 MANUAL로 기록합니다. 같은 약을 다시 호출해도 처음 기록을 유지합니다. nextMedication이 null이면 회차의 약을 다 먹은 것입니다.")
    @ApiResponse(responseCode = "200", description = "복용 기록 완료")
    @ApiResponse(responseCode = "403", description = "비활성 계정 (AUTH_005)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "404", description = "없는 회차 (SENIOR_003), 이 회차에 없는 약 (SENIOR_006)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "409", description = "끝난 회차 (SENIOR_004), 전화 받기 전 (SENIOR_008)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @PostMapping("/intake")
    public ResponseEntity<BaseResponse<IntakeResponse>> intake(@AuthenticationPrincipal Jwt jwt,
                                                               @PathVariable Long sessionId,
                                                               @PathVariable Long medicationId) {
        return ResponseEntity.ok(BaseResponse.success(intakeService.intake(accountId(jwt), sessionId, medicationId)));
    }

    private Long accountId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
