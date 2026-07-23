package com.hungtvb.votesystem.vote.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hungtvb.votesystem.vote.VoteType;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record VoteResponse(UUID postId, long voteScore, VoteType myVote) {
}
