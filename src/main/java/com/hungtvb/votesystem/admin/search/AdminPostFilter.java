package com.hungtvb.votesystem.admin.search;

import com.hungtvb.votesystem.post.BallotStatus;
import com.hungtvb.votesystem.post.ModerationStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminPostFilter(
        UUID id,
        String ballotNumber,
        String query,
        UUID authorId,
        String category,
        BallotStatus status,
        ModerationStatus moderationStatus,
        Instant createdFrom,
        Instant createdTo
) {
}
