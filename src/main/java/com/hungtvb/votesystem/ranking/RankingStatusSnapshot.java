package com.hungtvb.votesystem.ranking;

import java.time.Instant;

public record RankingStatusSnapshot(
        RankingAvailability availability,
        long visibleBallots,
        long eligibleDayBallots,
        long eligibleWeekBallots,
        Long hotMembers,
        Long topDayMembers,
        Long topWeekMembers,
        String generation,
        Instant lastSuccessfulRebuildAt,
        boolean rebuildInProgress
) {
}
