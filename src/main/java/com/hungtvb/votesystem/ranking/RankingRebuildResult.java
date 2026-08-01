package com.hungtvb.votesystem.ranking;

import java.time.Instant;

public record RankingRebuildResult(
        String lockToken,
        RedisRankingRepository.RankingGeneration generation,
        RedisRankingRepository.PublishState publishState,
        RedisRankingRepository.RankingCounts previousCounts,
        RedisRankingRepository.RankingCounts publishedCounts,
        long visiblePostCount,
        int redisBatchCount,
        long sourceRevision,
        Instant publishedAt
) {
}
