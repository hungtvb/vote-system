package com.hungtvb.votesystem.ranking;

import com.hungtvb.votesystem.vote.sideeffect.VoteSideEffectDispatcher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.hungtvb.votesystem.vote.sideeffect.VoteSideEffectDispatcher.Effect.RANKING;

@Component
public class RankingEventListener {
    private final RankingService rankingService;
    private final VoteSideEffectDispatcher dispatcher;

    public RankingEventListener(RankingService rankingService, VoteSideEffectDispatcher dispatcher) {
        this.rankingService = rankingService;
        this.dispatcher = dispatcher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRankingChanged(RankingChangedEvent event) {
        dispatcher.dispatch(RANKING, () -> rankingService.applyLatest(event.postId()));
    }
}
