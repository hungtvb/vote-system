package com.hungtvb.votesystem.admin.moderation;

import com.hungtvb.votesystem.admin.moderation.dto.AdminModerationReasonRequest;
import com.hungtvb.votesystem.admin.moderation.dto.AdminPostModerationResponse;
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
@RequestMapping("/api/v1/admin/posts")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPostModerationController {
    private final AdminPostModerationService service;

    public AdminPostModerationController(AdminPostModerationService service) {
        this.service = service;
    }

    @PostMapping("/{postId}/hide")
    public AdminPostModerationResponse hide(@AuthenticationPrincipal Jwt jwt,
                                            @PathVariable UUID postId,
                                            @Valid @RequestBody AdminModerationReasonRequest request) {
        return service.hide(actorId(jwt), postId, request.reason());
    }

    @PostMapping("/{postId}/restore")
    public AdminPostModerationResponse restore(@AuthenticationPrincipal Jwt jwt,
                                               @PathVariable UUID postId,
                                               @Valid @RequestBody AdminModerationReasonRequest request) {
        return service.restore(actorId(jwt), postId, request.reason());
    }

    @PostMapping("/{postId}/delete")
    public AdminPostModerationResponse softDelete(@AuthenticationPrincipal Jwt jwt,
                                                  @PathVariable UUID postId,
                                                  @Valid @RequestBody AdminModerationReasonRequest request) {
        return service.softDelete(actorId(jwt), postId, request.reason());
    }

    private UUID actorId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
