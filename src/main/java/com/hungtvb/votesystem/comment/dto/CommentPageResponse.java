package com.hungtvb.votesystem.comment.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CommentPageResponse(
        List<CommentResponse> content,
        Instant nextAfterCreatedAt,
        UUID nextAfterId,
        boolean hasMore
) {
}
