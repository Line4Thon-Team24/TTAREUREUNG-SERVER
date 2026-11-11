package com.ttareureung.api.domain.profile.dto;

import com.ttareureung.api.domain.profile.entity.Gender;
import com.ttareureung.api.domain.profile.entity.ProfileRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ProfileCreateRequest(
        @NotBlank(message = "이름을 입력해주세요.")
        @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
        @Schema(example = "김순자", maxLength = 50)
        String name,

        @PastOrPresent(message = "생년월일은 오늘 이후일 수 없습니다.")
        @Schema(example = "1950-03-15", description = "선택 입력")
        LocalDate birthDate,

        @Schema(example = "FEMALE", description = "선택 입력")
        Gender gender,

        @Pattern(regexp = "^\\d{2,3}-?\\d{3,4}-?\\d{4}$", message = "올바른 전화번호 형식이 아닙니다.")
        @Schema(example = "010-1234-5678", description = "선택 입력")
        String phoneNumber,

        @NotNull(message = "프로필 역할을 선택해주세요.")
        @Schema(example = "SENIOR")
        ProfileRole role
) {
    public ProfileCreateRequest {
        name = name == null ? null : name.strip();
        // 선택 항목을 빈 문자열로 보내는 경우도 입력하지 않은 것으로 처리
        phoneNumber = phoneNumber == null || phoneNumber.isBlank() ? null : phoneNumber.strip();
    }
}
