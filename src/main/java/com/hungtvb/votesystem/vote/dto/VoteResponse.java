package com.hungtvb.votesystem.vote.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hungtvb.votesystem.post.Post;
import com.hungtvb.votesystem.vote.VoteType;
import com.hungtvb.votesystem.vote.VoteVerdict;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record VoteResponse(
        UUID postId,
        long voteScore,
        long upVotes,
        long downVotes,
        long totalVotes,
        VoteType myVote,
        int verdictThreshold,
        VoteVerdict verdict
) {
    public static VoteResponse from(Post post, VoteType myVote, int verdictThreshold) {
        return new VoteResponse(
                post.getId(),
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