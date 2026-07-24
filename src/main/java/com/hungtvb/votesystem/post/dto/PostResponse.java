package com.hungtvb.votesystem.post.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hungtvb.votesystem.post.Post;
import com.hungtvb.votesystem.vote.VoteType;
import com.hungtvb.votesystem.vote.dto.VoteSummary;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PostResponse(
        UUID id,
        UUID authorId,
        String title,
        String content,
        VoteSummary votes,
        Instant createdAt,
        Instant updatedAt
) {
    public static PostResponse from(Post post, int verdictThreshold) {
        return from(post, null, verdictThreshold);
    }

    public static PostResponse from(Post post, VoteType myVote, int verdictThreshold) {
        return new PostResponse(
                post.getId(),
                post.getAuthorId(),
                post.getTitle(),
                post.getContent(),
                VoteSummary.from(post, myVote, verdictThreshold),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}