package com.hungtvb.votesystem.auth.metrics;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthRestoreMetricsTests {

    @Test
    void recordsSuccessfulAndFailedStagesWithBoundedTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuthRestoreMetrics metrics = new AuthRestoreMetrics(registry);

        String value = metrics.timeStage(AuthRestoreMetrics.OPERATION_REFRESH, "session_lock", () -> "ok");

        assertThat(value).isEqualTo("ok");
        Timer success = registry.get("vote.auth.restore.stage")
                .tags("operation", "refresh", "stage", "session_lock", "result", "success")
                .timer();
        assertThat(success.count()).isEqualTo(1);

        assertThatThrownBy(() -> metrics.timeStage(
                AuthRestoreMetrics.OPERATION_REFRESH,
                "session_lock",
                () -> {
                    throw new IllegalStateException("boom");
                }
        )).isInstanceOf(IllegalStateException.class);

        Timer error = registry.get("vote.auth.restore.stage")
                .tags("operation", "refresh", "stage", "session_lock", "result", "error")
                .timer();
        assertThat(error.count()).isEqualTo(1);
    }

    @Test
    void recordsTotalRestoreOutcomeWithoutIdentityTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuthRestoreMetrics metrics = new AuthRestoreMetrics(registry);

        AuthRestoreMetrics.RestoreSample sample = metrics.startTotal(AuthRestoreMetrics.OPERATION_REFRESH);
        metrics.stopTotal(sample, "success");

        Timer total = registry.get("vote.auth.restore.total")
                .tags("operation", "refresh", "outcome", "success")
                .timer();
        assertThat(total.count()).isEqualTo(1);
        assertThat(total.getId().getTags()).extracting(tag -> tag.getKey())
                .containsExactlyInAnyOrder("operation", "outcome");
    }
}
