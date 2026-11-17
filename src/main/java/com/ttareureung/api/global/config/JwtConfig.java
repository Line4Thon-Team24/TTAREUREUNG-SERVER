package com.ttareureung.api.global.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.ttareureung.api.global.security.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    public Clock jwtClock() {
        return Clock.systemUTC();
    }

    @Bean
    public SecretKey jwtSecretKey(JwtProperties properties) {
        byte[] key = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (key.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET은 최소 32바이트여야 합니다.");
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean("accessJwtDecoder")
    public JwtDecoder accessJwtDecoder(SecretKey jwtSecretKey, Clock jwtClock) {
        return decoder(jwtSecretKey, jwtClock, "access");
    }

    @Bean("refreshJwtDecoder")
    public JwtDecoder refreshJwtDecoder(SecretKey jwtSecretKey, Clock jwtClock) {
        return decoder(jwtSecretKey, jwtClock, "refresh");
    }

    private JwtDecoder decoder(SecretKey key, Clock clock, String type) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(jwt -> validateClaims(jwt, clock, type));
        return decoder;
    }

    private OAuth2TokenValidatorResult validateClaims(Jwt jwt, Clock clock, String type) {
        Instant now = clock.instant();
        if (jwt.getExpiresAt() == null || jwt.getIssuedAt() == null || !StringUtils.hasText(jwt.getId())) {
            return invalidToken();
        }
        if (!jwt.getExpiresAt().isAfter(now)) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("token_expired", "만료된 인증 정보입니다.", null));
        }
        if (jwt.getIssuedAt().isAfter(now) || (jwt.getNotBefore() != null && jwt.getNotBefore().isAfter(now))
                || !"ttareureung".equals(jwt.getClaimAsString("iss"))
                || !type.equals(jwt.getClaimAsString("token_type"))) {
            return invalidToken();
        }
        try {
            if (Long.parseLong(jwt.getSubject()) <= 0) {
                return invalidToken();
            }
        } catch (NumberFormatException e) {
            return invalidToken();
        }
        return OAuth2TokenValidatorResult.success();
    }

    private OAuth2TokenValidatorResult invalidToken() {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "유효하지 않은 인증 정보입니다.", null));
    }
}
