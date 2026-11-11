package com.ttareureung.api.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record LoginRequest(
        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 254, message = "이메일은 254자 이하여야 합니다.")
        @Schema(example = "guardian@example.com", maxLength = 254)
        String email,

        @NotBlank(message = "비밀번호를 입력해주세요.")
        // 형식은 검사하지 않고 길이만 막는다. 규칙 변경 전에 가입한 계정(특수문자 없음)도 로그인할 수 있어야 한다.
        @Size(max = 20, message = "비밀번호는 20자 이하여야 합니다.")
        @Schema(example = "Password123!", accessMode = Schema.AccessMode.WRITE_ONLY)
        String password
) {
    public LoginRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
