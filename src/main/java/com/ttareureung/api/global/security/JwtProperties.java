package com.ttareureung.api.global.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        @NotBlank String secret,
        @Positive long accessTokenExpiration,
        @Positive long refreshTokenExpiration,
        @NotNull @Valid Cookie cookie
) {
    public record Cookie(
            boolean secure,
            @NotBlank @Pattern(regexp = "Lax|Strict|None") String sameSite
    ) {
        public Cookie {
            if ("None".equals(sameSite) && !secure) {
                throw new IllegalArgumentException("SameSite=None 쿠키에는 Secure 설정이 필요합니다.");
            }
        }
    }
}
