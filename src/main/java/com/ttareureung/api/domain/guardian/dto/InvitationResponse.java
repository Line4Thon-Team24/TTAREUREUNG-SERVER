package com.ttareureung.api.domain.guardian.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

// SMS 없이 초대한 사람이 invite_url을 직접 전달(카톡 등). 대기 중인 초대는 같은 링크를 다시 돌려줌
public record InvitationResponse(
        @JsonProperty("invited_count") @Schema(description = "새로 만든 초대 수", example = "1") int invitedCount,
        String message,
        List<Item> invitations
) {
    public record Item(
            @JsonProperty("phone_number") @Schema(example = "01012345678") String phoneNumber,
            @Schema(description = "수락 API에 보낼 토큰") String token,
            @JsonProperty("invite_url") @Schema(description = "초대받은 사람에게 전달할 링크") String inviteUrl,
            @JsonProperty("expires_at") @Schema(example = "2026-11-15T15:00:00") LocalDateTime expiresAt,
            @JsonProperty("newly_created") @Schema(description = "false면 이미 보낸 초대를 다시 돌려준 것") boolean newlyCreated
    ) {
    }
}
