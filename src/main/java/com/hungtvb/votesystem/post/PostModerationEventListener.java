package com.hungtvb.votesystem.post;

import com.hungtvb.votesystem.vote.sideeffect.VoteSideEffectDispatcher;
import com.hungtvb.votesystem.vote.stream.BallotVoteStreamService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.hungtvb.votesystem.vote.sideeffect.VoteSideEffectDispatcher.Effect.SSE;

@Component
public class PostModerationEventListener {
    private final BallotVoteStreamService streamService;
    private final VoteSideEffectDispatcher dispatcher;

    public PostModerationEventListener(BallotVoteStreamService streamService,
                                       VoteSideEffectDispatcher dispatcher) {
        this.streamService = streamService;
        this.dispatcher = dispatcher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostModerationChanged(PostModerationChangedEvent event) {
        if (!event.publiclyVisible()) {
            dispatcher.dispatch(SSE, () -> streamService.closeSubscribers(event.postId()));
        }
    }
}
