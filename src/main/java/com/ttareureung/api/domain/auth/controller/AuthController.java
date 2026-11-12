package com.ttareureung.api.domain.auth.controller;

import com.ttareureung.api.domain.auth.RefreshTokenCookie;
import com.ttareureung.api.domain.auth.dto.LoginRequest;
import com.ttareureung.api.domain.auth.dto.SignupRequest;
import com.ttareureung.api.domain.auth.dto.SignupResponse;
import com.ttareureung.api.domain.auth.dto.TokenResponse;
import com.ttareureung.api.domain.auth.service.AuthService;
import com.ttareureung.api.global.common.BaseResponse;
import com.ttareureung.api.global.security.AuthRequestHeaderFilter;
import com.ttareureung.api.global.security.JwtTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "가족 계정 인증")
@SecurityRequirements // Access Token 없이 호출하는 API라 Swagger 전역 Bearer 설정에서 제외
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookie refreshTokenCookie;

    public AuthController(AuthService authService, RefreshTokenCookie refreshTokenCookie) {
        this.authService = authService;
        this.refreshTokenCookie = refreshTokenCookie;
    }

    @Operation(summary = "회원가입", description = "이메일을 정규화하고 가족 계정을 생성합니다.")
    @ApiResponse(responseCode = "201", description = "가입 성공")
    @ApiResponse(responseCode = "400", description = "이메일·비밀번호 입력 오류", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "403", description = "허용되지 않은 Origin", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "409", description = "이메일 중복 (AUTH_002)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @PostMapping(value = "/signup", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BaseResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        Long accountId = authService.signup(request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.success(new SignupResponse(accountId)));
    }

    @Operation(summary = "로그인", description = "Access Token을 반환하고 Refresh Token을 HttpOnly 쿠키에 저장합니다.")
    @ApiResponse(responseCode = "200", description = "로그인 성공")
    @ApiResponse(responseCode = "400", description = "입력값 오류", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치 (AUTH_001)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "403", description = "비활성 계정 (AUTH_005) 또는 허용되지 않은 Origin", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BaseResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        return tokensResponse(authService.login(request.email(), request.password()));
    }

    @Operation(summary = "토큰 재발급", description = "브라우저가 전달한 Refresh Cookie를 검증하고 새 토큰으로 교체합니다. 요청 본문은 없습니다.",
            parameters = @Parameter(name = AuthRequestHeaderFilter.HEADER_NAME, in = ParameterIn.HEADER, required = true,
                    schema = @Schema(type = "string", defaultValue = AuthRequestHeaderFilter.HEADER_VALUE,
                            allowableValues = AuthRequestHeaderFilter.HEADER_VALUE)))
    @ApiResponse(responseCode = "200", description = "재발급 성공")
    @ApiResponse(responseCode = "401", description = "Refresh Token 누락·만료·변조·재사용 (AUTH_003 / AUTH_004)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "403", description = "비활성 계정 또는 필수 헤더 누락", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @PostMapping("/refresh")
    public ResponseEntity<BaseResponse<TokenResponse>> refresh(
            @Parameter(hidden = true) @CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken) {
        return tokensResponse(authService.refresh(refreshToken));
    }

    @Operation(summary = "로그아웃", description = "현재 Refresh Token만 무효화하고 쿠키를 만료시킵니다. 클라이언트의 Access Token도 삭제해야 합니다.",
            parameters = @Parameter(name = AuthRequestHeaderFilter.HEADER_NAME, in = ParameterIn.HEADER, required = true,
                    schema = @Schema(type = "string", defaultValue = AuthRequestHeaderFilter.HEADER_VALUE,
                            allowableValues = AuthRequestHeaderFilter.HEADER_VALUE)))
    @ApiResponse(responseCode = "200", description = "로그아웃 성공")
    @ApiResponse(responseCode = "403", description = "필수 헤더 누락", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @PostMapping("/logout")
    public ResponseEntity<BaseResponse<Void>> logout(
            @Parameter(hidden = true) @CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, refreshTokenCookie.clear().toString())
                .body(BaseResponse.successWithoutData());
    }

    private ResponseEntity<BaseResponse<TokenResponse>> tokensResponse(JwtTokenService.IssuedTokens tokens) {
        TokenResponse response = new TokenResponse(tokens.accessToken(), "Bearer", tokens.expiresIn());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, refreshTokenCookie.create(tokens.refreshToken()).toString())
                .body(BaseResponse.success(response));
    }
}
