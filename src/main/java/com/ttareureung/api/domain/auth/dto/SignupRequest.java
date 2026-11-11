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

        // 와이어프레임 기준 8~20자, 영문·숫자·특수문자 포함
        // 공백·한글은 입력 실수 가능성이 커서 제외, ASCII 출력 문자(\x21-\x7E) 중 영문·숫자 외 문자를 특수문자로 취급
        @NotBlank(message = "비밀번호를 입력해주세요.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^A-Za-z0-9])[\\x21-\\x7E]{8,20}$",
                message = "비밀번호는 영문, 숫자, 특수문자를 각각 포함한 8~20자여야 합니다.")
        @Schema(example = "Password123!", description = "영문·숫자·특수문자 포함 8~20자", accessMode = Schema.AccessMode.WRITE_ONLY)
        String password
) {
    public SignupRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
