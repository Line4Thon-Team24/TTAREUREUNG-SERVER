package com.ttareureung.api.domain.medication.controller;

import com.ttareureung.api.domain.medication.dto.MonthlyCalendarResponse;
import com.ttareureung.api.domain.medication.dto.SeniorMedicationSummaryResponse;
import com.ttareureung.api.domain.medication.service.MedicationSummaryService;
import com.ttareureung.api.domain.profile.exception.ProfileErrorCode;
import com.ttareureung.api.global.common.BaseResponse;
import com.ttareureung.api.global.exception.CustomException;
import com.ttareureung.api.global.exception.GlobalErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/medications")
@Tag(name = "MedicationSummary", description = "복약 요약·캘린더")
public class MedicationSummaryController {

    private final MedicationSummaryService summaryService;

    public MedicationSummaryController(MedicationSummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @Operation(summary = "월별 복용 캘린더", description = "month(yyyy-MM)의 날짜별 복용 상태와 복용률을 반환합니다. 복용률 = 1회 이상 복용한 날 ÷ 그 달의 일수.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "400", description = "month 형식 오류", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "404", description = "없거나 접근 불가한 시니어 (PROFILE_002)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @GetMapping("/calendar")
    public ResponseEntity<BaseResponse<MonthlyCalendarResponse>> calendar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("senior_id") Long seniorId,
            @RequestParam("month") @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return ResponseEntity.ok(BaseResponse.success(summaryService.getMonthly(accountId(jwt), seniorId, month)));
    }

    @Operation(summary = "시니어별 약 요약", description = "시니어 목록 카드용. 등록 여부와 복용 중인 약 개수(종료일이 오늘 이후)를 반환합니다. 최대 50명.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "400", description = "senior_ids 비었거나 50개 초과", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "404", description = "접근 불가한 시니어 포함 (PROFILE_002)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @GetMapping("/summary")
    public ResponseEntity<BaseResponse<SeniorMedicationSummaryResponse>> summary(
            @AuthenticationPrincipal Jwt jwt, @RequestParam("senior_ids") List<Long> seniorIds) {
        if (seniorIds.isEmpty() || seniorIds.size() > 50) {
            throw new CustomException(GlobalErrorCode.INVALID_INPUT);
        }
        return ResponseEntity.ok(BaseResponse.success(summaryService.summarize(accountId(jwt), seniorIds)));
    }

    private Long accountId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
