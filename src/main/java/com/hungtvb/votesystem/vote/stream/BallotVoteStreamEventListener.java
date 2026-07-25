package com.hungtvb.votesystem.vote.stream;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class BallotVoteStreamEventListener {
    private final BallotVoteStreamService streamService;

    public BallotVoteStreamEventListener(BallotVoteStreamService streamService) {
        this.streamService = streamService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVoteChanged(BallotVoteChangedEvent event) {
        streamService.publish(event.update());
    }
}
