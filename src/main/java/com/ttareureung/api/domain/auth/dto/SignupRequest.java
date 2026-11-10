package com.ttareureung.api.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record SignupRequest(
        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 254, message = "이메일은 254자 이하여야 합니다.")
        @Schema(example = "guardian@example.com", maxLength = 254)
        String email,

        @NotBlank(message = "비밀번호를 입력해주세요.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9])[A-Za-z0-9]{8,16}$",
                message = "비밀번호는 영문과 숫자를 각각 포함한 8~16자여야 합니다.")
        @Schema(example = "Password123", description = "영문·숫자 조합 8~16자", accessMode = Schema.AccessMode.WRITE_ONLY)
        String password
) {
    public SignupRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
