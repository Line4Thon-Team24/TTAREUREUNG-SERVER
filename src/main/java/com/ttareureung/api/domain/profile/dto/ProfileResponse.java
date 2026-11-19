package com.ttareureung.api.domain.profile.dto;

import com.ttareureung.api.domain.profile.entity.Gender;
import com.ttareureung.api.domain.profile.entity.Profile;
import com.ttareureung.api.domain.profile.entity.ProfileRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ProfileResponse(
        @Schema(example = "1") Long profileId,
        @Schema(example = "김순자") String name,
        @Schema(example = "1950-03-15") LocalDate birthDate,
        @Schema(example = "FEMALE") Gender gender,
        @Schema(example = "010-1234-5678") String phoneNumber,
        @Schema(example = "어머니") String relationship,
        @Schema(example = "SENIOR") ProfileRole role,
        @Schema(description = "로그인 이메일. 가족 공용 계정이라 같은 계정의 프로필은 모두 같은 값", example = "guardian@example.com") String email,
        @Schema(description = "즐겨찾기 여부. 가족 계정 공통", example = "false") boolean isFavorite,
        @Schema(description = "등록 일시. 시니어 카드 \"등록 날짜\", 최근 추가순 정렬", example = "2026-11-05T19:23:00") LocalDateTime createdAt,
        @Schema(description = "보호자면 담당 시니어, 시니어면 담당 보호자") List<ProfileSummary> linkedProfiles
) {
    public static ProfileResponse of(Profile profile, List<ProfileSummary> linkedProfiles) {
        return new ProfileResponse(profile.getId(), profile.getName(), profile.getBirthDate(),
                profile.getGender(), profile.getPhoneNumber(), profile.getRelationship(), profile.getRole(),
                profile.getAccount().getEmail(), profile.isFavorite(), profile.getCreatedAt(), linkedProfiles);
    }
}
