package com.hungtvb.votesystem.auth.session;

import com.hungtvb.votesystem.auth.session.dto.SessionResponse;
import com.hungtvb.votesystem.auth.session.dto.SessionRevocationResponse;
import com.hungtvb.votesystem.common.error.InvalidRequestException;
import com.hungtvb.votesystem.security.TokenService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/sessions")
public class SessionManagementController {
    private final SessionManagementService service;

    public SessionManagementController(SessionManagementService service) {
        this.service = service;
    }

    @GetMapping
    public List<SessionResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(userId(jwt), currentFamilyIdOrNull(jwt));
    }

    @DeleteMapping("/others")
    public SessionRevocationResponse revokeOthers(@AuthenticationPrincipal Jwt jwt) {
        return service.revokeOthers(userId(jwt), requireCurrentFamilyId(jwt));
    }

    @DeleteMapping("/{sessionId}")
    public SessionRevocationResponse revoke(@AuthenticationPrincipal Jwt jwt,
                                            @PathVariable UUID sessionId) {
        return service.revoke(userId(jwt), requireCurrentFamilyId(jwt), sessionId);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private UUID currentFamilyIdOrNull(Jwt jwt) {
        String value = jwt.getClaimAsString(TokenService.SESSION_FAMILY_CLAIM);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private UUID requireCurrentFamilyId(Jwt jwt) {
        UUID familyId = currentFamilyIdOrNull(jwt);
        if (familyId == null) {
            throw new InvalidRequestException("Refresh authentication before managing sessions");
        }
        return familyId;
    }
}
