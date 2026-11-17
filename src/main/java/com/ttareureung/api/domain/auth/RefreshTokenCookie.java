package com.ttareureung.api.domain.auth;

import com.ttareureung.api.global.security.JwtProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RefreshTokenCookie {

    public static final String NAME = "refresh_token";
    private final JwtProperties properties;

    public RefreshTokenCookie(JwtProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie create(String token) {
        return build(token, Duration.ofMillis(properties.refreshTokenExpiration()));
    }

    public ResponseCookie clear() {
        return build("", Duration.ZERO);
    }

    private ResponseCookie build(String token, Duration maxAge) {
        return ResponseCookie.from(NAME, token)
                .httpOnly(true)
                .secure(properties.cookie().secure())
                .sameSite(properties.cookie().sameSite())
                .path("/api/v1/auth")
                .maxAge(maxAge)
                .build();
    }
}
