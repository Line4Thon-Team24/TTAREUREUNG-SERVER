package com.ttareureung.api.domain.profile.dto;

import com.ttareureung.api.domain.profile.entity.Gender;
import com.ttareureung.api.domain.profile.entity.Profile;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

// 연결 정보에 함께 내려주는 상대 프로필 요약. 보호자 목록 카드(생년월일·성별·전화번호)에도 사용
public record ProfileSummary(
        @Schema(example = "2") Long profileId,
        @Schema(example = "김순자") String name,
        @Schema(example = "어머니") String relationship,
        @Schema(example = "1950-03-15") LocalDate birthDate,
        @Schema(example = "FEMALE") Gender gender,
        @Schema(example = "010-1234-5678") String phoneNumber
) {
    public static ProfileSummary from(Profile profile) {
        return new ProfileSummary(profile.getId(), profile.getName(), profile.getRelationship(),
                profile.getBirthDate(), profile.getGender(), profile.getPhoneNumber());
    }
}
