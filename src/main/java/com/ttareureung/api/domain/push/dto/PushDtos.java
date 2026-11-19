package com.ttareureung.api.domain.push.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class PushDtos {

    private PushDtos() {
    }

    // 브라우저 PushSubscription.toJSON() 결과에 profileId만 더해서 보내면 됨
    public record SubscribeRequest(
            @NotNull @JsonAlias("profile_id") @Schema(example = "4") Long profileId,
            @NotBlank @Size(max = 500) @Pattern(regexp = "^https://.*", message = "푸시 endpoint는 https 주소여야 합니다.")
            @Schema(example = "https://fcm.googleapis.com/fcm/send/abc...") String endpoint,
            @NotNull @Valid Keys keys
    ) {
    }

    public record Keys(
            @NotBlank @Size(max = 200) @Schema(description = "수신자 공개키(base64url)") String p256dh,
            @NotBlank @Size(max = 100) @Schema(description = "인증 비밀값(base64url)") String auth
    ) {
    }

    public record UnsubscribeRequest(
            @NotBlank @Size(max = 500) String endpoint
    ) {
    }

    public record SubscribeResponse(
            @Schema(example = "3") Long subscriptionId,
            @Schema(example = "4") Long profileId
    ) {
    }

    public record VapidKeyResponse(
            @Schema(description = "서버에 키가 설정돼 푸시를 보낼 수 있는지") boolean enabled,
            @Schema(description = "applicationServerKey로 쓰는 공개키(base64url). 미설정이면 null") String publicKey
    ) {
    }
}
