package com.hungtvb.votesystem.auth.session;

import com.hungtvb.votesystem.common.config.RefreshTokenProperties;
import com.hungtvb.votesystem.common.error.UnauthorizedException;
import com.hungtvb.votesystem.security.AuthenticatedUser;
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
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshSessionService(RefreshSessionRepository sessionRepository,
                                 UserRepository userRepository,
                                 RefreshTokenProperties properties) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.properties = properties;
    }

    @Transactional
    public RefreshGrant issue(AuthenticatedUser user) {
        IssuedToken issuedToken = createSession(user.id(), Instant.now());
        return new RefreshGrant(user, issuedToken.rawToken(), properties.ttl().toSeconds());
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public RefreshGrant rotate(String rawToken) {
        String tokenHash = hash(requireToken(rawToken));
        Instant now = Instant.now();
        RefreshSession current = sessionRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(this::invalidSession);

        if (current.isRevoked()) {
            if (current.wasRotated()) {
                sessionRepository.revokeAllActiveByUserId(current.getUserId(), now);
            }
            throw invalidSession();
        }

        if (current.isExpired(now)) {
            current.revoke(now);
            throw invalidSession();
        }

        AppUser user = userRepository.findById(current.getUserId())
                .orElseThrow(this::invalidSession);
        IssuedToken replacement = createSession(current.getUserId(), now);
        current.rotateTo(replacement.sessionId(), now);

        return new RefreshGrant(
                AuthenticatedUser.from(user),
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
            throw invalidSession();
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

    private UnauthorizedException invalidSession() {
        return new UnauthorizedException(INVALID_SESSION_MESSAGE);
    }

    private record IssuedToken(UUID sessionId, String rawToken) {
    }
}
