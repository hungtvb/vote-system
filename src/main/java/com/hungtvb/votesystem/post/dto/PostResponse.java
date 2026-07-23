package com.hungtvb.votesystem.post.dto;

import com.hungtvb.votesystem.post.Post;
import java.time.Instant;
import java.util.UUID;

public record PostResponse(
        UUID id,
        UUID authorId,
        String title,
        String content,
        long voteScore,
        Instant createdAt,
        Instant updatedAt
) {
    public static PostResponse from(Post post) {
        return new PostResponse(post.getId(), post.getAuthorId(), post.getTitle(), post.getContent(),
                post.getVoteScore(), post.getCreatedAt(), post.getUpdatedAt());
    }
}
