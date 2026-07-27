package com.hungtvb.votesystem.vote.sideeffect;

import com.hungtvb.votesystem.vote.metrics.VoteLatencyMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

import static com.hungtvb.votesystem.vote.metrics.VoteLatencyMetrics.OPERATION_POST_COMMIT;

@Component
public class VoteSideEffectDispatcher {
    private static final Logger log = LoggerFactory.getLogger(VoteSideEffectDispatcher.class);

    private final TaskExecutor rankingExecutor;
    private final TaskExecutor sseExecutor;
    private final VoteLatencyMetrics latencyMetrics;
    private final MeterRegistry meterRegistry;

    public VoteSideEffectDispatcher(
            @Qualifier("voteRankingExecutor") TaskExecutor rankingExecutor,
            @Qualifier("voteSseExecutor") TaskExecutor sseExecutor,
            VoteLatencyMetrics latencyMetrics,
            MeterRegistry meterRegistry) {
        this.rankingExecutor = rankingExecutor;
        this.sseExecutor = sseExecutor;
        this.latencyMetrics = latencyMetrics;
        this.meterRegistry = meterRegistry;
    }

    public void dispatch(Effect effect, Runnable action) {
        Runnable measuredTask = () -> run(effect, action);
        try {
            executor(effect).execute(measuredTask);
            counter(effect, "accepted").increment();
        } catch (TaskRejectedException exception) {
            counter(effect, "caller_runs").increment();
            measuredTask.run();
        }
    }

    private TaskExecutor executor(Effect effect) {
        return effect == Effect.RANKING ? rankingExecutor : sseExecutor;
    }

    private void run(Effect effect, Runnable action) {
        try {
            latencyMetrics.timeStage(OPERATION_POST_COMMIT, effect.stage(), action);
            counter(effect, "completed").increment();
        } catch (RuntimeException exception) {
            counter(effect, "failed").increment();
            log.error("Vote side effect failed effect={}", effect.tag(), exception);
        }
    }

    private Counter counter(Effect effect, String result) {
        return meterRegistry.counter(
                "vote.side_effect.execution",
                "effect", effect.tag(),
                "result", result
        );
    }

    public enum Effect {
        RANKING("ranking", "ranking_listener"),
        SSE("sse", "sse_listener");

        private final String tag;
        private final String stage;

        Effect(String tag, String stage) {
            this.tag = tag;
            this.stage = stage;
        }

        public String tag() {
            return tag;
        }

        public String stage() {
            return stage;
        }
    }
}
