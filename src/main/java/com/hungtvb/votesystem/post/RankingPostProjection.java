package com.hungtvb.votesystem.post;

import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight ranking rebuild row. Keeping the projection outside the ranking package
 * prevents a bounded scan from materializing full ballot entities.
 */
public interface RankingPostProjection {
    UUID getId();

    long getVoteScore();

    Instant getCreatedAt();
}
