package com.hungtvb.votesystem.ranking;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class RankingEventListener {
    private final RankingService rankingService;

    public RankingEventListener(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRankingChanged(RankingChangedEvent event) {
        rankingService.apply(event);
    }
}
