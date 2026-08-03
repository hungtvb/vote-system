package com.hungtvb.votesystem.auth.session;

import com.hungtvb.votesystem.auth.metrics.AuthRestoreMetrics;
import com.hungtvb.votesystem.common.config.RefreshTokenProperties;
import com.hungtvb.votesystem.common.error.ConflictException;
import com.hungtvb.votesystem.common.error.ResourceNotFoundException;
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
import java.util.List;
import java.util.Optional;
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
    private final AccountSecurityEventService securityEvents;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshSessionService(RefreshSessionRepository sessionRepository,
                                 UserRepository userRepository,
                                 RefreshTokenProperties properties,
                                 AccountAccessPolicy accountAccessPolicy,
                                 AuthRestoreMetrics metrics,
                                 AccountSecurityEventService securityEvents) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.properties = properties;
        this.accountAccessPolicy = accountAccessPolicy;
        this.metrics = metrics;
        this.securityEvents = securityEvents;
    }

    @Transactional
    public RefreshGrant issue(AppUser user, SessionClientMetadata metadata) {
        accountAccessPolicy.requireActive(user);
        Instant now = Instant.now();
        IssuedToken issuedToken = createNewSession(user.getId(), now, metadata);
        securityEvents.recordSignIn(issuedToken.session(), now);
        return grant(user, issuedToken);
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public RefreshGrant rotate(String rawToken) {
        String requiredToken = requireToken(rawToken);
        String tokenHash = metrics.timeStage(
                AuthRestoreMetrics.OPERATION_REFRESH,
                "token_hash",
                () -> hash(requiredToken)
        );
        UUID userId = sessionRepository.findUserIdByTokenHash(tokenHash)
                .orElseThrow(() -> invalidSession(RefreshSessionFailureException.Reason.INVALID));
        Instant now = Instant.now();

        AppUser user = metrics.timeStage(
                AuthRestoreMetrics.OPERATION_REFRESH,
                "user_lookup",
                () -> userRepository.findByIdForUpdate(userId)
                        .orElseThrow(() -> invalidSession(RefreshSessionFailureException.Reason.INVALID))
        );
        RefreshSession current = metrics.timeStage(
                AuthRestoreMetrics.OPERATION_REFRESH,
                "session_lock",
                () -> sessionRepository.findByTokenHashForUpdate(tokenHash)
                        .filter(session -> session.getUserId().equals(user.getId()))
                        .orElseThrow(() -> invalidSession(RefreshSessionFailureException.Reason.INVALID))
        );

        if (current.isRevoked()) {
            if (current.wasRotated()) {
                List<RefreshSession> revoked = metrics.timeStage(
                        AuthRestoreMetrics.OPERATION_REFRESH,
                        "reuse_revoke_all",
                        () -> revokeLocked(sessionRepository.findAllActiveByUserIdForUpdate(user.getId()), now)
                );
                securityEvents.recordRevocations(revoked, now);
                securityEvents.recordSuspiciousReuse(current, now);
                throw invalidSession(RefreshSessionFailureException.Reason.REUSED);
            }
            throw invalidSession(RefreshSessionFailureException.Reason.REVOKED);
        }

        if (current.isExpired(now)) {
            current.revoke(now);
            securityEvents.recordRevocations(List.of(current), now);
            throw invalidSession(RefreshSessionFailureException.Reason.EXPIRED);
        }

        try {
            accountAccessPolicy.requireActive(user, now);
        } catch (UnauthorizedException exception) {
            List<RefreshSession> revoked = metrics.timeStage(
                    AuthRestoreMetrics.OPERATION_REFRESH,
                    "access_revoke_all",
                    () -> revokeLocked(sessionRepository.findAllActiveByUserIdForUpdate(user.getId()), now)
            );
            securityEvents.recordRevocations(revoked, now);
            throw exception;
        }

        IssuedToken replacement = metrics.timeStage(
                AuthRestoreMetrics.OPERATION_REFRESH,
                "rotation_write",
                () -> rotateToReplacement(current, now)
        );

        return grant(user, replacement);
    }

    @Transactional
    public Optional<UUID> revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        String tokenHash = hash(rawToken);
        Optional<UUID> ownerId = sessionRepository.findUserIdByTokenHash(tokenHash);
        if (ownerId.isEmpty()) {
            return Optional.empty();
        }

        UUID userId = ownerId.get();
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UnauthorizedException("User account is unavailable"));
        Optional<RefreshSession> locked = sessionRepository.findByTokenHashForUpdate(tokenHash);
        if (locked.isEmpty() || !locked.get().getUserId().equals(userId)) {
            return Optional.empty();
        }

        Instant now = Instant.now();
        RefreshSession current = locked.get();
        if (current.isRevoked()) {
            return Optional.empty();
        }
        if (current.isExpired(now)) {
            current.revoke(now);
            securityEvents.recordRevocations(List.of(current), now);
            return Optional.empty();
        }

        current.revoke(now);
        securityEvents.recordRevocations(List.of(current), now);
        return Optional.of(current.getUserId());
    }

    @Transactional
    public int revokeAll(UUID userId) {
        lockUser(userId);
        return revokeAndRecord(sessionRepository.findAllActiveByUserIdForUpdate(userId), Instant.now());
    }

    @Transactional
    public int revokeOtherSessions(UUID userId, UUID currentFamilyId) {
        lockUser(userId);
        requireActiveFamily(userId, currentFamilyId);
        return revokeAndRecord(
                sessionRepository.findOtherActiveFamiliesForUpdate(userId, currentFamilyId),
                Instant.now()
        );
    }

    @Transactional
    public int revokeOtherFamily(UUID userId, UUID currentFamilyId, UUID targetFamilyId) {
        if (currentFamilyId.equals(targetFamilyId)) {
            throw new ConflictException("The current session cannot be revoked from this endpoint");
        }
        lockUser(userId);
        requireActiveFamily(userId, currentFamilyId);
        List<RefreshSession> target = sessionRepository.findActiveFamilyForUpdate(userId, targetFamilyId);
        if (target.isEmpty()) {
            throw new ResourceNotFoundException("Session not found");
        }
        return revokeAndRecord(target, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<RefreshSession> activeSessions(UUID userId) {
        return sessionRepository.findAllActiveByUserId(userId);
    }

    private void requireActiveFamily(UUID userId, UUID familyId) {
        if (sessionRepository.findActiveFamilyForUpdate(userId, familyId).isEmpty()) {
            throw new ConflictException("Current refresh session is unavailable");
        }
    }

    private AppUser lockUser(UUID userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UnauthorizedException("User account is unavailable"));
    }

    private int revokeAndRecord(List<RefreshSession> sessions, Instant now) {
        List<RefreshSession> revoked = revokeLocked(sessions, now);
        securityEvents.recordRevocations(revoked, now);
        return revoked.size();
    }

    private List<RefreshSession> revokeLocked(List<RefreshSession> sessions, Instant now) {
        return sessions.stream()
                .filter(session -> session.revoke(now))
                .toList();
    }

    private IssuedToken createNewSession(UUID userId,
                                         Instant now,
                                         SessionClientMetadata metadata) {
        String rawToken = generateToken();
        RefreshSession session = RefreshSession.start(
                userId,
                hash(rawToken),
                now,
                now.plus(properties.ttl()),
                metadata
        );
        return save(session, rawToken);
    }

    private IssuedToken rotateToReplacement(RefreshSession current, Instant now) {
        String rawToken = generateToken();
        RefreshSession replacement = current.replacement(
                hash(rawToken),
                now,
                now.plus(properties.ttl())
        );

        // The active-family unique index requires the current row to be retired before
        // the replacement is inserted. The replacement link is written last because its
        // self-referencing foreign key is immediate, not deferred.
        current.revoke(now);
        sessionRepository.flush();
        RefreshSession saved = sessionRepository.saveAndFlush(replacement);
        current.rotateTo(saved.getId(), now);
        sessionRepository.flush();
        return new IssuedToken(saved, rawToken);
    }

    private IssuedToken save(RefreshSession session, String rawToken) {
        RefreshSession saved = sessionRepository.saveAndFlush(session);
        return new IssuedToken(saved, rawToken);
    }

    private RefreshGrant grant(AppUser user, IssuedToken issuedToken) {
        return new RefreshGrant(
                user,
                issuedToken.session().getFamilyId(),
                issuedToken.rawToken(),
                properties.ttl().toSeconds()
        );
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

    private record IssuedToken(RefreshSession session, String rawToken) {
    }
}
