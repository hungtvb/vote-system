package com.hungtvb.votesystem.vote.stream;

import com.hungtvb.votesystem.post.Post;
import com.hungtvb.votesystem.vote.VoteVerdict;

import java.time.Instant;
import java.util.UUID;

public record BallotVoteUpdate(
        UUID postId,
        long voteScore,
        long upVotes,
        long downVotes,
        long totalVotes,
        int verdictThreshold,
        VoteVerdict verdict,
        Instant updatedAt
) {
    public static BallotVoteUpdate from(Post post) {
        return new BallotVoteUpdate(
                post.getId(),
                post.getVoteScore(),
                post.getUpVotes(),
                post.getDownVotes(),
                post.getTotalVotes(),
                post.getVerdictThreshold(),
                VoteVerdict.from(post.getUpVotes(), post.getDownVotes(), post.getVerdictThreshold()),
                post.getUpdatedAt());
    }

    public String eventId() {
        return updatedAt.toString();
    }
}
