package com.hungtvb.votesystem.auth.session;

import com.hungtvb.votesystem.auth.metrics.AuthRestoreMetrics;
import com.hungtvb.votesystem.common.config.RefreshTokenProperties;
import com.hungtvb.votesystem.common.error.UnauthorizedException;
import com.hungtvb.votesystem.user.AccountAccessPolicy;
import com.hungtvb.votesystem.user.AppUser;
import com.hungtvb.votesystem.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class RefreshSessionService {
    private static final int TOKEN_BYTES = 48;
    private static final String INVALID_SESSION_MESSAGE = "Refresh session is invalid or expired";

    private final RefreshSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final RefreshTokenProperties properties;
    private final AccountAccessPolicy accountAccessPolicy;
    private final AuthRestoreMetrics metrics;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshSessionService(RefreshSessionRepository sessionRepository,
                                 UserRepository userRepository,
                                 RefreshTokenProperties properties,
                                 AccountAccessPolicy accountAccessPolicy,
                                 AuthRestoreMetrics metrics) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.properties = properties;
        this.accountAccessPolicy = accountAccessPolicy;
        this.metrics = metrics;
    }

    @Transactional
    public RefreshGrant issue(AppUser user) {
        accountAccessPolicy.requireActive(user);
        IssuedToken issuedToken = createSession(user.getId(), Instant.now());
        return new RefreshGrant(user, issuedToken.rawToken(), properties.ttl().toSeconds());
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public RefreshGrant rotate(String rawToken) {
        String requiredToken = requireToken(rawToken);
        String tokenHash = metrics.timeStage(
                AuthRestoreMetrics.OPERATION_REFRESH,
                "token_hash",
                () -> hash(requiredToken)
        );
        Instant now = Instant.now();
        RefreshSession current = metrics.timeStage(
                AuthRestoreMetrics.OPERATION_REFRESH,
                "session_lock",
                () -> sessionRepository.findByTokenHashForUpdate(tokenHash)
                        .orElseThrow(() -> invalidSession(RefreshSessionFailureException.Reason.INVALID))
        );

        if (current.isRevoked()) {
            if (current.wasRotated()) {
                metrics.timeStage(
                        AuthRestoreMetrics.OPERATION_REFRESH,
                        "reuse_revoke_all",
                        () -> sessionRepository.revokeAllActiveByUserId(current.getUserId(), now)
                );
                throw invalidSession(RefreshSessionFailureException.Reason.REUSED);
            }
            throw invalidSession(RefreshSessionFailureException.Reason.REVOKED);
        }

        if (current.isExpired(now)) {
            metrics.timeStage(
                    AuthRestoreMetrics.OPERATION_REFRESH,
                    "rotation_write",
                    () -> current.revoke(now)
            );
            throw invalidSession(RefreshSessionFailureException.Reason.EXPIRED);
        }

        AppUser user = metrics.timeStage(
                AuthRestoreMetrics.OPERATION_REFRESH,
                "user_lookup",
                () -> userRepository.findByIdForUpdate(current.getUserId())
                        .orElseThrow(() -> invalidSession(RefreshSessionFailureException.Reason.INVALID))
        );
        try {
            accountAccessPolicy.requireActive(user, now);
        } catch (UnauthorizedException exception) {
            metrics.timeStage(
                    AuthRestoreMetrics.OPERATION_REFRESH,
                    "access_revoke_all",
                    () -> sessionRepository.revokeAllActiveByUserId(current.getUserId(), now)
            );
            throw exception;
        }

        IssuedToken replacement = metrics.timeStage(
                AuthRestoreMetrics.OPERATION_REFRESH,
                "rotation_write",
                () -> {
                    IssuedToken issuedToken = createSession(current.getUserId(), now);
                    current.rotateTo(issuedToken.sessionId(), now);
                    return issuedToken;
                }
        );

        return new RefreshGrant(
                user,
                replacement.rawToken(),
                properties.ttl().toSeconds()
        );
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        Instant now = Instant.now();
        sessionRepository.findByTokenHashForUpdate(hash(rawToken))
                .ifPresent(session -> session.revoke(now));
    }

    @Transactional
    public int revokeAll(UUID userId) {
        return sessionRepository.revokeAllActiveByUserId(userId, Instant.now());
    }

    private IssuedToken createSession(UUID userId, Instant now) {
        String rawToken = generateToken();
        RefreshSession session = RefreshSession.create(
                userId,
                hash(rawToken),
                now,
                now.plus(properties.ttl())
        );
        RefreshSession saved = sessionRepository.saveAndFlush(session);
        return new IssuedToken(saved.getId(), rawToken);
    }

    private String requireToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidSession(RefreshSessionFailureException.Reason.NO_COOKIE);
        }
        return rawToken;
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private RefreshSessionFailureException invalidSession(RefreshSessionFailureException.Reason reason) {
        return new RefreshSessionFailureException(INVALID_SESSION_MESSAGE, reason);
    }

    private record IssuedToken(UUID sessionId, String rawToken) {
    }
}
