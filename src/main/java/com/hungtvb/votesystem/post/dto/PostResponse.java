package com.hungtvb.votesystem.post.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hungtvb.votesystem.post.BallotStatus;
import com.hungtvb.votesystem.post.Post;
import com.hungtvb.votesystem.vote.VoteType;
import com.hungtvb.votesystem.vote.VoteVerdict;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PostResponse(
        UUID id,
        UUID authorId,
        String ballotNumber,
        String title,
        String content,
        String category,
        BallotStatus status,
        Instant closesAt,
        Instant closedAt,
        long voteScore,
        long upVotes,
        long downVotes,
        long totalVotes,
        VoteType myVote,
        int verdictThreshold,
        VoteVerdict verdict,
        boolean finalVerdict,
        Instant createdAt,
        Instant updatedAt
) {
    public static PostResponse from(Post post, VoteType myVote) {
        VoteVerdict verdict = post.getFinalVerdict() != null
                ? post.getFinalVerdict()
                : VoteVerdict.from(post.getUpVotes(), post.getDownVotes(), post.getVerdictThreshold());
        return new PostResponse(
                post.getId(),
                post.getAuthorId(),
                post.getBallotNumber(),
                post.getTitle(),
                post.getContent(),
                post.getCategory(),
                post.getStatus(),
                post.getClosesAt(),
                post.getClosedAt(),
                post.getVoteScore(),
                post.getUpVotes(),
                post.getDownVotes(),
                post.getTotalVotes(),
                myVote,
                post.getVerdictThreshold(),
                verdict,
                post.getFinalVerdict() != null,
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
