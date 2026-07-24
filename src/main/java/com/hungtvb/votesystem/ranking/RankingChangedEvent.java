package com.hungtvb.votesystem.ranking;

import java.time.Instant;
import java.util.UUID;

public record RankingChangedEvent(UUID postId, long voteScore, Instant createdAt, boolean deleted) {
    public static RankingChangedEvent upsert(UUID postId, long voteScore, Instant createdAt) {
        return new RankingChangedEvent(postId, voteScore, createdAt, false);
    }

    public static RankingChangedEvent delete(UUID postId, Instant createdAt) {
        return new RankingChangedEvent(postId, 0, createdAt, true);
    }
}
