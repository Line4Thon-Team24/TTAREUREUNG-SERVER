package com.ttareureung.api.domain.profile.controller;

import com.ttareureung.api.domain.profile.dto.ProfileCreateRequest;
import com.ttareureung.api.domain.profile.dto.ProfileResponse;
import com.ttareureung.api.domain.profile.service.ProfileService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 로그인한 가족 계정의 프로필 API.
 * Access Token 검증은 Spring Security가 먼저 처리하고, 여기서는 검증된 토큰의 subject(accountId)만 사용한다.
 */
@RestController
@RequestMapping("/api/v1/profiles")
@Tag(name = "Profile", description = "가족 계정 프로필")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @Operation(summary = "프로필 생성", description = "로그인한 계정에 보호자 또는 시니어 프로필을 추가합니다. 회원가입 후 첫 보호자 등록과 시니어 등록 모두 이 API를 사용하며, 시니어는 보호자 프로필이 있어야 만들 수 있습니다.")
    @ApiResponse(responseCode = "201", description = "생성 성공")
    @ApiResponse(responseCode = "400", description = "입력값 오류", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "401", description = "Access Token 누락·만료", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "403", description = "비활성 계정 (AUTH_005)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "409", description = "보호자 프로필 없이 시니어 생성 (PROFILE_001)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BaseResponse<ProfileResponse>> create(@AuthenticationPrincipal Jwt jwt,
                                                                @Valid @RequestBody ProfileCreateRequest request) {
        ProfileResponse response = profileService.create(accountId(jwt), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.success(response));
    }

    @Operation(summary = "프로필 목록 조회", description = "로그인한 계정의 프로필을 생성 순서대로 반환합니다. 프로필 선택 화면에서 사용합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "401", description = "Access Token 누락·만료", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @GetMapping
    public ResponseEntity<BaseResponse<List<ProfileResponse>>> findAll(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(BaseResponse.success(profileService.findAll(accountId(jwt))));
    }

    // JwtConfig에서 subject가 양수 숫자인지 이미 검증했기 때문에 바로 변환해도 된다.
    private Long accountId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
