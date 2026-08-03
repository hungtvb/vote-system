package com.hungtvb.votesystem.auth.session;

import com.hungtvb.votesystem.auth.session.dto.SessionResponse;
import com.hungtvb.votesystem.auth.session.dto.SessionRevocationResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SessionManagementService {
    private final RefreshSessionService refreshSessionService;

    public SessionManagementService(RefreshSessionService refreshSessionService) {
        this.refreshSessionService = refreshSessionService;
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> list(UUID userId, UUID currentFamilyId) {
        return refreshSessionService.activeSessions(userId).stream()
                .map(session -> SessionResponse.from(session, currentFamilyId))
                .toList();
    }

    @Transactional
    public SessionRevocationResponse revoke(UUID userId,
                                            UUID currentFamilyId,
                                            UUID targetFamilyId) {
        return new SessionRevocationResponse(
                refreshSessionService.revokeOtherFamily(userId, currentFamilyId, targetFamilyId));
    }

    @Transactional
    public SessionRevocationResponse revokeOthers(UUID userId, UUID currentFamilyId) {
        return new SessionRevocationResponse(
                refreshSessionService.revokeOtherSessions(userId, currentFamilyId));
    }
}
