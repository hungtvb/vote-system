package com.hungtvb.votesystem.vote.dto;

import com.hungtvb.votesystem.vote.VoteType;
import jakarta.validation.constraints.NotNull;

public record VoteRequest(@NotNull VoteType type) {
}
