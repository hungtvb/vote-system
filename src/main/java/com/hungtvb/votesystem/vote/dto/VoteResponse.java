package com.hungtvb.votesystem.vote.dto;

import com.hungtvb.votesystem.vote.VoteType;
import java.util.UUID;

public record VoteResponse(UUID postId, long voteScore, VoteType myVote) {
}
