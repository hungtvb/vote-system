package com.hungtvb.votesystem.post.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hungtvb.votesystem.post.Post;
import com.hungtvb.votesystem.vote.VoteType;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PostResponse(
        UUID id,
        UUID authorId,
        String title,
        String content,
        long voteScore,
        VoteType myVote,
        Instant createdAt,
        Instant updatedAt
) {
    public static PostResponse from(Post post) {
        return from(post, null);
    }

    public static PostResponse from(Post post, VoteType myVote) {
        return new PostResponse(post.getId(), post.getAuthorId(), post.getTitle(), post.getContent(),
                post.getVoteScore(), myVote, post.getCreatedAt(), post.getUpdatedAt());
    }
}
