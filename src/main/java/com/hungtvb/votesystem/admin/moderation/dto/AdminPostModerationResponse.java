package com.hungtvb.votesystem.admin.moderation.dto;

import com.hungtvb.votesystem.post.ModerationStatus;
import com.hungtvb.votesystem.post.Post;

import java.time.Instant;
import java.util.UUID;

public record AdminPostModerationResponse(
        UUID id,
        ModerationStatus moderationStatus,
        Instant moderationUpdatedAt
) {
    public static AdminPostModerationResponse from(Post post) {
        return new AdminPostModerationResponse(
                post.getId(),
                post.getModerationStatus(),
                post.getModerationUpdatedAt()
        );
    }
}
