package com.hungtvb.votesystem.admin.search.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hungtvb.votesystem.post.BallotStatus;
import com.hungtvb.votesystem.post.ModerationStatus;
import com.hungtvb.votesystem.post.Post;
import com.hungtvb.votesystem.post.dto.AuthorSummary;
import com.hungtvb.votesystem.vote.VoteVerdict;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminPostResponse(
        UUID id,
        UUID authorId,
        AuthorSummary author,
        String ballotNumber,
        String title,
        String content,
        String category,
        BallotStatus status,
        ModerationStatus moderationStatus,
        Instant moderationUpdatedAt,
        Instant closesAt,
        Instant closedAt,
        long voteScore,
        long upVotes,
        long downVotes,
        long totalVotes,
        int verdictThreshold,
        VoteVerdict verdict,
        boolean finalVerdict,
        Instant createdAt,
        Instant updatedAt
) {
    public static AdminPostResponse from(Post post, AuthorSummary author) {
        VoteVerdict verdict = post.getFinalVerdict() != null
                ? post.getFinalVerdict()
                : VoteVerdict.from(post.getUpVotes(), post.getDownVotes(), post.getVerdictThreshold());
        return new AdminPostResponse(
                post.getId(),
                post.getAuthorId(),
                author,
                post.getBallotNumber(),
                post.getTitle(),
                post.getContent(),
                post.getCategory(),
                post.getStatus(),
                post.getModerationStatus(),
                post.getModerationUpdatedAt(),
                post.getClosesAt(),
                post.getClosedAt(),
                post.getVoteScore(),
                post.getUpVotes(),
                post.getDownVotes(),
                post.getTotalVotes(),
                post.getVerdictThreshold(),
                verdict,
                post.getFinalVerdict() != null,
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
