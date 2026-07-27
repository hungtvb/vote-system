package com.hungtvb.votesystem.vote.sideeffect;

import com.hungtvb.votesystem.vote.metrics.VoteLatencyMetrics;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.hungtvb.votesystem.vote.sideeffect.VoteSideEffectDispatcher.Effect.RANKING;
import static com.hungtvb.votesystem.vote.sideeffect.VoteSideEffectDispatcher.Effect.SSE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class VoteSideEffectDispatcherTests {

    @Test
    void acceptedTaskRunsOutsideTheDispatchCallAndRecordsCompletion() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AtomicReference<Runnable> queued = new AtomicReference<>();
        TaskExecutor rankingExecutor = queued::set;
        VoteSideEffectDispatcher dispatcher = dispatcher(rankingExecutor, Runnable::run, registry);
        AtomicBoolean ran = new AtomicBoolean();

        dispatcher.dispatch(RANKING, () -> ran.set(true));

        assertThat(ran).isFalse();
        assertThat(registry.get("vote.side_effect.execution")
                .tags("effect", "ranking", "result", "accepted")
                .counter().count()).isEqualTo(1);

        queued.get().run();

        assertThat(ran).isTrue();
        assertThat(registry.get("vote.side_effect.execution")
                .tags("effect", "ranking", "result", "completed")
                .counter().count()).isEqualTo(1);
        Timer stage = registry.get("vote.operation.stage")
                .tags("operation", "post_commit", "stage", "ranking_listener", "result", "success")
                .timer();
        assertThat(stage.count()).isEqualTo(1);
    }

    @Test
    void rejectedTaskFallsBackToCallerWithoutThrowingOrLosingWork() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TaskExecutor rejectedExecutor = task -> {
            throw new TaskRejectedException("queue full");
        };
        VoteSideEffectDispatcher dispatcher = dispatcher(Runnable::run, rejectedExecutor, registry);
        AtomicInteger runs = new AtomicInteger();

        assertThatCode(() -> dispatcher.dispatch(SSE, runs::incrementAndGet))
                .doesNotThrowAnyException();

        assertThat(runs).hasValue(1);
        assertThat(registry.get("vote.side_effect.execution")
                .tags("effect", "sse", "result", "caller_runs")
                .counter().count()).isEqualTo(1);
        assertThat(registry.get("vote.side_effect.execution")
                .tags("effect", "sse", "result", "completed")
                .counter().count()).isEqualTo(1);
    }

    @Test
    void sideEffectFailureIsMeteredAndDoesNotEscape() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TaskExecutor directExecutor = Runnable::run;
        VoteSideEffectDispatcher dispatcher = dispatcher(directExecutor, directExecutor, registry);

        assertThatCode(() -> dispatcher.dispatch(SSE, () -> {
            throw new IllegalStateException("downstream failed");
        })).doesNotThrowAnyException();

        assertThat(registry.get("vote.side_effect.execution")
                .tags("effect", "sse", "result", "accepted")
                .counter().count()).isEqualTo(1);
        assertThat(registry.get("vote.side_effect.execution")
                .tags("effect", "sse", "result", "failed")
                .counter().count()).isEqualTo(1);
        Timer stage = registry.get("vote.operation.stage")
                .tags("operation", "post_commit", "stage", "sse_listener", "result", "error")
                .timer();
        assertThat(stage.count()).isEqualTo(1);
    }

    private VoteSideEffectDispatcher dispatcher(
            TaskExecutor rankingExecutor,
            TaskExecutor sseExecutor,
            SimpleMeterRegistry registry) {
        return new VoteSideEffectDispatcher(
                rankingExecutor,
                sseExecutor,
                new VoteLatencyMetrics(registry),
                registry
        );
    }
}
