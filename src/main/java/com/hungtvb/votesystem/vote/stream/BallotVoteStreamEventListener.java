package com.hungtvb.votesystem.vote.stream;

import com.hungtvb.votesystem.vote.metrics.VoteLatencyMetrics;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.hungtvb.votesystem.vote.metrics.VoteLatencyMetrics.OPERATION_POST_COMMIT;

@Component
public class BallotVoteStreamEventListener {
    private final BallotVoteStreamService streamService;
    private final VoteLatencyMetrics metrics;

    public BallotVoteStreamEventListener(BallotVoteStreamService streamService, VoteLatencyMetrics metrics) {
        this.streamService = streamService;
        this.metrics = metrics;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVoteChanged(BallotVoteChangedEvent event) {
        metrics.timeStage(OPERATION_POST_COMMIT, "sse_listener", () -> streamService.publish(event.update()));
    }
}
