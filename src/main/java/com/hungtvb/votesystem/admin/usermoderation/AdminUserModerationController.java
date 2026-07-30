package com.hungtvb.votesystem.admin.usermoderation;

import com.hungtvb.votesystem.admin.usermoderation.dto.AdminUserModerationResponse;
import com.hungtvb.votesystem.admin.usermoderation.dto.AdminUserReasonRequest;
import com.hungtvb.votesystem.admin.usermoderation.dto.AdminUserRestrictionRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserModerationController {
    private final AdminUserModerationService service;

    public AdminUserModerationController(AdminUserModerationService service) {
        this.service = service;
    }

    @PostMapping("/{userId}/suspend")
    public AdminUserModerationResponse suspend(@AuthenticationPrincipal Jwt jwt,
                                               @PathVariable UUID userId,
                                               @Valid @RequestBody AdminUserRestrictionRequest request) {
        return service.suspend(actorId(jwt), userId, request.reason(), request.until());
    }

    @PostMapping("/{userId}/ban")
    public AdminUserModerationResponse ban(@AuthenticationPrincipal Jwt jwt,
                                           @PathVariable UUID userId,
                                           @Valid @RequestBody AdminUserRestrictionRequest request) {
        return service.ban(actorId(jwt), userId, request.reason(), request.until());
    }

    @PostMapping("/{userId}/restore")
    public AdminUserModerationResponse restore(@AuthenticationPrincipal Jwt jwt,
                                               @PathVariable UUID userId,
                                               @Valid @RequestBody AdminUserReasonRequest request) {
        return service.restore(actorId(jwt), userId, request.reason());
    }

    @PostMapping("/{userId}/revoke-sessions")
    public AdminUserModerationResponse revokeSessions(@AuthenticationPrincipal Jwt jwt,
                                                      @PathVariable UUID userId,
                                                      @Valid @RequestBody AdminUserReasonRequest request) {
        return service.revokeSessions(actorId(jwt), userId, request.reason());
    }

    private UUID actorId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
