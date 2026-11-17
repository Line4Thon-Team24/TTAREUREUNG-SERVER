package com.ttareureung.api.domain.auth.service;

import com.ttareureung.api.domain.auth.entity.RefreshToken;
import com.ttareureung.api.domain.auth.exception.AuthErrorCode;
import com.ttareureung.api.domain.auth.repository.RefreshTokenRepository;
import com.ttareureung.api.domain.user.entity.Account;
import com.ttareureung.api.domain.user.entity.AccountStatus;
import com.ttareureung.api.domain.user.repository.AccountRepository;
import com.ttareureung.api.global.exception.CustomException;
import com.ttareureung.api.global.security.JwtTokenService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

@Service
@Transactional
public class AuthService {

    private final AccountRepository accounts;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthService(AccountRepository accounts, RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService, Clock clock) {
        this.accounts = accounts;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public Long signup(String email, String password) {
        if (accounts.existsByEmail(email)) {
            throw new CustomException(AuthErrorCode.EMAIL_DUPLICATED);
        }
        Account account = new Account(email, passwordEncoder.encode(password));
        try {
            return accounts.saveAndFlush(account).getId();
        } catch (DataIntegrityViolationException e) {
            if (e.getMostSpecificCause() instanceof SQLException sql && sql.getErrorCode() == 1062) {
                throw new CustomException(AuthErrorCode.EMAIL_DUPLICATED);
            }
            throw e;
        }
    }

    public JwtTokenService.IssuedTokens login(String email, String password) {
        Account account = accounts.findByEmail(email).orElse(null);
        String encodedPassword = account == null ? dummyPasswordHash : account.getPassword();
        if (!passwordEncoder.matches(password, encodedPassword) || account == null) {
            throw new CustomException(AuthErrorCode.INVALID_CREDENTIALS);
        }
        requireActive(account);
        return issueAndStore(account);
    }

    public JwtTokenService.IssuedTokens refresh(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            throw new CustomException(AuthErrorCode.INVALID_TOKEN);
        }
        var jwt = jwtTokenService.validateRefresh(rawToken);
        RefreshToken current = refreshTokens.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_TOKEN));
        Account account = current.getAccount();
        if (!String.valueOf(account.getId()).equals(jwt.getSubject())) {
            throw new CustomException(AuthErrorCode.INVALID_TOKEN);
        }
        if (!current.getExpiresAt().isAfter(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))) {
            throw new CustomException(AuthErrorCode.TOKEN_EXPIRED);
        }
        requireActive(account);
        refreshTokens.delete(current);
        return issueAndStore(account);
    }

    public void logout(String rawToken) {
        if (StringUtils.hasText(rawToken)) {
            refreshTokens.findByTokenHashForUpdate(hash(rawToken)).ifPresent(refreshTokens::delete);
        }
    }

    private JwtTokenService.IssuedTokens issueAndStore(Account account) {
        var tokens = jwtTokenService.issue(account.getId());
        refreshTokens.save(new RefreshToken(account, hash(tokens.refreshToken()),
                LocalDateTime.ofInstant(tokens.refreshExpiresAt(), ZoneOffset.UTC)));
        return tokens;
    }

    private void requireActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new CustomException(AuthErrorCode.ACCOUNT_INACTIVE);
        }
    }

    private String hash(String rawToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 사용 불가", e);
        }
    }
}
