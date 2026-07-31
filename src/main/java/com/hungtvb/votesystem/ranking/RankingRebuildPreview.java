package com.hungtvb.votesystem.ranking;

import java.time.Instant;

public record RankingRebuildPreview(
        RedisRankingRepository.RankingGeneration generation,
        RedisRankingRepository.RankingMetadata previousMetadata,
        RedisRankingRepository.RankingCounts previousCounts,
        RedisRankingRepository.RankingCounts stagedCounts,
        int visiblePostCount,
        long sourceRevision,
        Instant preparedAt
) {
}
