package com.hungtvb.votesystem.vote.dto;

import com.hungtvb.votesystem.post.Post;
import com.hungtvb.votesystem.vote.VoteType;
import com.hungtvb.votesystem.vote.VoteVerdict;

public record VoteSummary(
        long voteScore,
        long upVotes,
        long downVotes,
        long totalVotes,
        VoteType myVote,
        int verdictThreshold,
        VoteVerdict verdict
) {
    public static VoteSummary from(Post post, VoteType myVote, int verdictThreshold) {
        return new VoteSummary(
                post.getVoteScore(),
                post.getUpVotes(),
                post.getDownVotes(),
                post.getTotalVotes(),
                myVote,
                verdictThreshold,
                VoteVerdict.from(post.getUpVotes(), post.getDownVotes(), verdictThreshold)
        );
    }
}