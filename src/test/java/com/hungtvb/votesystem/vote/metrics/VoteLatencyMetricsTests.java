package com.hungtvb.votesystem.vote.metrics;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VoteLatencyMetricsTests {

    @Test
    void recordsSuccessfulAndFailedStagesWithBoundedTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        VoteLatencyMetrics metrics = new VoteLatencyMetrics(registry);

        String value = metrics.timeStage(VoteLatencyMetrics.OPERATION_CAST, "post_lock", () -> "ok");

        assertThat(value).isEqualTo("ok");
        Timer success = registry.get("vote.operation.stage")
                .tags("operation", "cast", "stage", "post_lock", "result", "success")
                .timer();
        assertThat(success.count()).isEqualTo(1);

        assertThatThrownBy(() -> metrics.timeStage(
                VoteLatencyMetrics.OPERATION_CAST,
                "post_lock",
                () -> {
                    throw new IllegalStateException("boom");
                }
        )).isInstanceOf(IllegalStateException.class);

        Timer error = registry.get("vote.operation.stage")
                .tags("operation", "cast", "stage", "post_lock", "result", "error")
                .timer();
        assertThat(error.count()).isEqualTo(1);
    }

    @Test
    void recordsTotalVoteOutcomeWithoutIdentityTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        VoteLatencyMetrics metrics = new VoteLatencyMetrics(registry);

        VoteLatencyMetrics.VoteSample sample = metrics.startTotal(VoteLatencyMetrics.OPERATION_REMOVE);
        metrics.stopTotal(sample, "success");

        Timer total = registry.get("vote.operation.total")
                .tags("operation", "remove", "outcome", "success")
                .timer();
        assertThat(total.count()).isEqualTo(1);
        assertThat(total.getId().getTags()).extracting(tag -> tag.getKey())
                .containsExactlyInAnyOrder("operation", "outcome");
    }
}
