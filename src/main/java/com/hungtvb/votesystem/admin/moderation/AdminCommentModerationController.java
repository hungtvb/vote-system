package com.hungtvb.votesystem.admin.moderation;

import com.hungtvb.votesystem.admin.moderation.dto.AdminCommentModerationResponse;
import com.hungtvb.votesystem.admin.moderation.dto.AdminModerationReasonRequest;
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
@RequestMapping("/api/v1/admin/comments")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCommentModerationController {
    private final AdminCommentModerationService service;

    public AdminCommentModerationController(AdminCommentModerationService service) {
        this.service = service;
    }

    @PostMapping("/{commentId}/hide")
    public AdminCommentModerationResponse hide(@AuthenticationPrincipal Jwt jwt,
                                               @PathVariable UUID commentId,
                                               @Valid @RequestBody AdminModerationReasonRequest request) {
        return service.hide(actorId(jwt), commentId, request.reason());
    }

    @PostMapping("/{commentId}/restore")
    public AdminCommentModerationResponse restore(@AuthenticationPrincipal Jwt jwt,
                                                  @PathVariable UUID commentId,
                                                  @Valid @RequestBody AdminModerationReasonRequest request) {
        return service.restore(actorId(jwt), commentId, request.reason());
    }

    @PostMapping("/{commentId}/remove")
    public AdminCommentModerationResponse remove(@AuthenticationPrincipal Jwt jwt,
                                                 @PathVariable UUID commentId,
                                                 @Valid @RequestBody AdminModerationReasonRequest request) {
        return service.remove(actorId(jwt), commentId, request.reason());
    }

    private UUID actorId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
