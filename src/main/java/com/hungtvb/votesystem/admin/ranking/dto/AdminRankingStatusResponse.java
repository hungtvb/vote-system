package com.hungtvb.votesystem.admin.ranking.dto;

import com.hungtvb.votesystem.ranking.RankingAvailability;
import com.hungtvb.votesystem.ranking.RankingStatusSnapshot;

import java.time.Instant;

public record AdminRankingStatusResponse(
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
    public static AdminRankingStatusResponse from(RankingStatusSnapshot snapshot) {
        return new AdminRankingStatusResponse(snapshot.availability(), snapshot.visibleBallots(),
                snapshot.eligibleDayBallots(), snapshot.eligibleWeekBallots(), snapshot.hotMembers(),
                snapshot.topDayMembers(), snapshot.topWeekMembers(), snapshot.generation(),
                snapshot.lastSuccessfulRebuildAt(), snapshot.rebuildInProgress());
    }
}
