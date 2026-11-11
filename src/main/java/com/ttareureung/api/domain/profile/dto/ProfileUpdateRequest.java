package com.ttareureung.api.domain.profile.dto;

import com.ttareureung.api.domain.profile.entity.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 프로필 부분 수정 요청. null(미전송) 필드는 기존 값 유지
 * 전화번호·관계는 빈 문자열이면 삭제. null과 구분하기 위해 빈 문자열을 null로 바꾸지 않음
 * role은 수정 대상 아님 (본문에 넣어도 무시)
 */
public record ProfileUpdateRequest(
        // 공백만 보내면 strip 후 빈 문자열이라 min = 1에서 거부
        @Size(min = 1, max = 50, message = "이름은 1~50자여야 합니다.")
        @Schema(example = "김순자", maxLength = 50)
        String name,

        @PastOrPresent(message = "생년월일은 오늘 이후일 수 없습니다.")
        @Schema(example = "1950-03-15")
        LocalDate birthDate,

        @Schema(example = "FEMALE")
        Gender gender,

        @Pattern(regexp = "^$|^\\d{2,3}-?\\d{3,4}-?\\d{4}$", message = "올바른 전화번호 형식이 아닙니다.")
        @Schema(example = "010-1234-5678", description = "빈 문자열을 보내면 삭제")
        String phoneNumber,

        @Size(max = 20, message = "관계는 20자 이하여야 합니다.")
        @Schema(example = "어머니", description = "빈 문자열을 보내면 삭제", maxLength = 20)
        String relationship
) {
    public ProfileUpdateRequest {
        name = name == null ? null : name.strip();
        phoneNumber = phoneNumber == null ? null : phoneNumber.strip();
        relationship = relationship == null ? null : relationship.strip();
    }
}
