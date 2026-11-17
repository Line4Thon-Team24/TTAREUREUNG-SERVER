package com.ttareureung.api.global.security;

import com.ttareureung.api.domain.auth.exception.AuthErrorCode;
import com.ttareureung.api.global.exception.CustomException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class JwtTokenService {

    private final JwtEncoder encoder;
    private final JwtDecoder refreshDecoder;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtTokenService(JwtEncoder encoder, @Qualifier("refreshJwtDecoder") JwtDecoder refreshDecoder,
                           JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.refreshDecoder = refreshDecoder;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedTokens issue(Long accountId) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant accessExpiry = now.plusMillis(properties.accessTokenExpiration());
        Instant refreshExpiry = now.plusMillis(properties.refreshTokenExpiration());
        return new IssuedTokens(encode(accountId, "access", now, accessExpiry),
                encode(accountId, "refresh", now, refreshExpiry), refreshExpiry,
                properties.accessTokenExpiration() / 1000);
    }

    public Jwt validateRefresh(String token) {
        try {
            return refreshDecoder.decode(token);
        } catch (JwtValidationException e) {
            boolean expired = e.getErrors().stream().anyMatch(error -> "token_expired".equals(error.getErrorCode()));
            throw new CustomException(expired ? AuthErrorCode.TOKEN_EXPIRED : AuthErrorCode.INVALID_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            throw new CustomException(AuthErrorCode.INVALID_TOKEN);
        }
    }

    private String encode(Long accountId, String type, Instant now, Instant expiry) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("ttareureung")
                .subject(String.valueOf(accountId))
                .issuedAt(now)
                .expiresAt(expiry)
                .id(UUID.randomUUID().toString())
                .claim("token_type", type)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    public record IssuedTokens(String accessToken, String refreshToken, Instant refreshExpiresAt, long expiresIn) {
        @Override
        public String toString() {
            return "IssuedTokens[redacted]";
        }
    }
}
