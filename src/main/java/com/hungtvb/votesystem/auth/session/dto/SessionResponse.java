package com.hungtvb.votesystem.auth.session.dto;

import com.hungtvb.votesystem.auth.session.RefreshSession;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt,
        String provider,
        String clientLabel,
        boolean current
) {
    public static SessionResponse from(RefreshSession session, UUID currentFamilyId) {
        return new SessionResponse(
                session.getFamilyId(),
                session.getStartedAt(),
                session.getLastUsedAt(),
                session.getExpiresAt(),
                session.getProvider().name(),
                session.getClientLabel(),
                session.getFamilyId().equals(currentFamilyId)
        );
    }
}
