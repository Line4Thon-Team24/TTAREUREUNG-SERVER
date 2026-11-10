package com.ttareureung.api.global.security;

import com.ttareureung.api.domain.auth.RefreshTokenCookie;
import com.ttareureung.api.domain.auth.exception.AuthErrorCode;
import com.ttareureung.api.global.config.JwtConfig;
import com.ttareureung.api.global.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTests {

    private static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final JwtProperties properties = new JwtProperties(
            "unit-test-secret-key-at-least-32-bytes", 1800000, 604800000,
            new JwtProperties.Cookie(false, "Lax"));
    private final JwtConfig config = new JwtConfig();
    private final javax.crypto.SecretKey key = config.jwtSecretKey(properties);
    private final org.springframework.security.oauth2.jwt.JwtEncoder encoder = config.jwtEncoder(key);
    private final JwtTokenService service = new JwtTokenService(encoder,
            config.refreshJwtDecoder(key, clock), properties, clock);

    @Test
    void accessAndRefreshTokensUseAccountSubjectAndSeparateLifetimes() {
        var tokens = service.issue(42L);
        var access = config.accessJwtDecoder(key, clock).decode(tokens.accessToken());
        var refresh = service.validateRefresh(tokens.refreshToken());
        assertThat(access.getSubject()).isEqualTo("42");
        assertThat(refresh.getSubject()).isEqualTo("42");
        assertThat(access.getExpiresAt()).isEqualTo(NOW.plusSeconds(1800));
        assertThat(refresh.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(access.getClaimAsString("token_type")).isEqualTo("access");
        assertThat(refresh.getClaimAsString("token_type")).isEqualTo("refresh");
        assertThat(access.getId()).isNotEqualTo(refresh.getId());
        assertThat(tokens.toString()).doesNotContain(tokens.accessToken(), tokens.refreshToken());
    }

    @Test
    void refreshTokenExpiresAtItsExactExpiryWithoutClockSkewAllowance() {
        var tokens = service.issue(42L);
        Clock expiryClock = Clock.fixed(NOW.plus(Duration.ofDays(7)), ZoneOffset.UTC);
        var expiredService = new JwtTokenService(encoder, config.refreshJwtDecoder(key, expiryClock), properties, expiryClock);
        assertThatThrownBy(() -> expiredService.validateRefresh(tokens.refreshToken()))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.TOKEN_EXPIRED));
    }

    @Test
    void accessTokenExpiresAtThirtyMinutes() {
        var tokens = service.issue(42L);
        Clock expiryClock = Clock.fixed(NOW.plusSeconds(1800), ZoneOffset.UTC);
        assertThatThrownBy(() -> config.accessJwtDecoder(key, expiryClock).decode(tokens.accessToken()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void accessTokenCannotBeUsedForRefresh() {
        assertThatThrownBy(() -> service.validateRefresh(service.issue(42L).accessToken()))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_TOKEN));
    }

    @ParameterizedTest(name = "invalid subject case {index}")
    @ValueSource(strings = {"0", "-1", "account", "9223372036854775808"})
    void rejectsInvalidAccountSubject(String subject) {
        String token = sign(JwtClaimsSet.builder().issuer("ttareureung").subject(subject)
                .issuedAt(NOW).expiresAt(NOW.plusSeconds(3600)).id("test")
                .claim("token_type", "refresh").build());
        assertThatThrownBy(() -> service.validateRefresh(token)).isInstanceOf(CustomException.class);
    }

    @Test
    void rejectsMissingExpiryAndWrongIssuer() {
        String missingExpiry = sign(JwtClaimsSet.builder().issuer("ttareureung").subject("42")
                .issuedAt(NOW).id("test").claim("token_type", "refresh").build());
        String wrongIssuer = sign(JwtClaimsSet.builder().issuer("other-service").subject("42")
                .issuedAt(NOW).expiresAt(NOW.plusSeconds(3600)).id("test")
                .claim("token_type", "refresh").build());
        assertThatThrownBy(() -> service.validateRefresh(missingExpiry)).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> service.validateRefresh(wrongIssuer)).isInstanceOf(CustomException.class);
    }

    @Test
    void rejectsShortSecretWithoutIncludingItInErrorMessage() {
        var shortProperties = new JwtProperties("short-secret", 1800000, 604800000,
                new JwtProperties.Cookie(false, "Lax"));
        assertThatThrownBy(() -> config.jwtSecretKey(shortProperties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("short-secret");
    }

    @Test
    void productionCookieIsSecureAndClearedWithMatchingScope() {
        var production = new JwtProperties(properties.secret(), 1800000, 604800000,
                new JwtProperties.Cookie(true, "Lax"));
        var cookie = new RefreshTokenCookie(production);
        assertThat(cookie.create("fixture-token").toString())
                .contains("Secure", "HttpOnly", "Path=/api/v1/auth", "SameSite=Lax");
        assertThat(cookie.clear().toString()).contains("Secure", "HttpOnly", "Path=/api/v1/auth", "Max-Age=0");
        assertThatThrownBy(() -> new JwtProperties.Cookie(false, "None"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private String sign(JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
