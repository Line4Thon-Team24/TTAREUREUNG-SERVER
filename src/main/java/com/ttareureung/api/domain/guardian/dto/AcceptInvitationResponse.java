package com.ttareureung.api.domain.guardian.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

public record AcceptInvitationResponse(
        @JsonProperty("senior_profile_id") @Schema(example = "2") Long seniorProfileId,
        @JsonProperty("senior_name") @Schema(example = "김순자") String seniorName,
        @JsonProperty("link_id") @Schema(description = "연결 해제 시 사용", example = "7") Long linkId
) {
}
