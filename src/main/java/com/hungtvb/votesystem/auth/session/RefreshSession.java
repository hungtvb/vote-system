package com.hungtvb.votesystem.auth.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_sessions")
public class RefreshSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_session_id")
    private UUID replacedBySessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 16)
    private SessionProvider provider;

    @Column(name = "client_label", nullable = false, length = 64)
    private String clientLabel;

    protected RefreshSession() {
    }

    private RefreshSession(UUID userId,
                           UUID familyId,
                           String tokenHash,
                           Instant createdAt,
                           Instant startedAt,
                           Instant expiresAt,
                           SessionProvider provider,
                           String clientLabel) {
        this.userId = userId;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.expiresAt = expiresAt;
        this.lastUsedAt = createdAt;
        this.provider = provider;
        this.clientLabel = clientLabel;
    }

    public static RefreshSession create(UUID userId,
                                        String tokenHash,
                                        Instant now,
                                        Instant expiresAt) {
        return start(userId, tokenHash, now, expiresAt,
                new SessionClientMetadata(SessionProvider.PASSWORD, "UNKNOWN"));
    }

    public static RefreshSession start(UUID userId,
                                       String tokenHash,
                                       Instant now,
                                       Instant expiresAt,
                                       SessionClientMetadata metadata) {
        return new RefreshSession(
                userId,
                UUID.randomUUID(),
                tokenHash,
                now,
                now,
                expiresAt,
                metadata.provider(),
                metadata.clientLabel()
        );
    }

    public RefreshSession replacement(String tokenHash, Instant now, Instant expiresAt) {
        return new RefreshSession(
                userId,
                familyId,
                tokenHash,
                now,
                startedAt,
                expiresAt,
                provider,
                clientLabel
        );
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean wasRotated() {
        return replacedBySessionId != null;
    }

    public void rotateTo(UUID replacementSessionId, Instant now) {
        this.replacedBySessionId = replacementSessionId;
        this.lastUsedAt = now;
        this.revokedAt = now;
    }

    public boolean revoke(Instant now) {
        if (this.revokedAt != null) {
            return false;
        }
        this.revokedAt = now;
        this.lastUsedAt = now;
        return true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public SessionProvider getProvider() {
        return provider;
    }

    public String getClientLabel() {
        return clientLabel;
    }
}
