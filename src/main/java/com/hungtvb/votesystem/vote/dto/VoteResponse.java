package com.hungtvb.votesystem.vote.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record VoteResponse(UUID postId, VoteSummary votes) {
}