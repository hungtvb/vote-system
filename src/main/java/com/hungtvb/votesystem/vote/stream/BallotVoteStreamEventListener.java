package com.hungtvb.votesystem.vote.stream;

import com.hungtvb.votesystem.vote.sideeffect.VoteSideEffectDispatcher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.hungtvb.votesystem.vote.sideeffect.VoteSideEffectDispatcher.Effect.SSE;

@Component
public class BallotVoteStreamEventListener {
    private final BallotVoteStreamService streamService;
    private final VoteSideEffectDispatcher dispatcher;

    public BallotVoteStreamEventListener(BallotVoteStreamService streamService,
                                         VoteSideEffectDispatcher dispatcher) {
        this.streamService = streamService;
        this.dispatcher = dispatcher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVoteChanged(BallotVoteChangedEvent event) {
        dispatcher.dispatch(SSE, () -> streamService.publish(event.update()));
    }
}
