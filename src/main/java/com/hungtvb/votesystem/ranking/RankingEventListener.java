package com.hungtvb.votesystem.ranking;

import com.hungtvb.votesystem.vote.metrics.VoteLatencyMetrics;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.hungtvb.votesystem.vote.metrics.VoteLatencyMetrics.OPERATION_POST_COMMIT;

@Component
public class RankingEventListener {
    private final RankingService rankingService;
    private final VoteLatencyMetrics metrics;

    public RankingEventListener(RankingService rankingService, VoteLatencyMetrics metrics) {
        this.rankingService = rankingService;
        this.metrics = metrics;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRankingChanged(RankingChangedEvent event) {
        metrics.timeStage(OPERATION_POST_COMMIT, "ranking_listener", () -> rankingService.apply(event));
    }
}
