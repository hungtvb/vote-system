package com.hungtvb.votesystem.system;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SystemModeMetricsTests {

    @Test
    void recordsOnlyBoundedModeAndRouteTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SystemModeMetrics metrics = new SystemModeMetrics(registry);

        metrics.recordRejection(
                SystemModeEnforcementFilter.READ_ONLY_CODE,
                SystemMode.READ_ONLY,
                "PUT",
                "/api/v1/posts/00000000-0000-0000-0000-000000000001/vote?token=secret"
        );

        Counter counter = registry.get("vote.system.mode.requests")
                .tags(
                        "result", "rejected",
                        "status", "503",
                        "code", SystemModeEnforcementFilter.READ_ONLY_CODE,
                        "mode", "READ_ONLY",
                        "method", "PUT",
                        "route", "posts"
                )
                .counter();

        assertThat(counter.count()).isEqualTo(1);
        assertThat(counter.getId().getTags())
                .noneMatch(tag -> tag.getValue().contains("00000000-0000")
                        || tag.getValue().contains("secret"));
    }

    @Test
    void normalizesUnknownInputsToBoundedValues() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SystemModeMetrics metrics = new SystemModeMetrics(registry);

        metrics.recordRejection("CUSTOM_" + System.nanoTime(), null, "BREW", "/tenant/private/value");

        Counter counter = registry.get("vote.system.mode.requests")
                .tags(
                        "result", "rejected",
                        "status", "503",
                        "code", "UNKNOWN",
                        "mode", "UNKNOWN",
                        "method", "OTHER",
                        "route", "other"
                )
                .counter();
        assertThat(counter.count()).isEqualTo(1);
    }

    @Test
    void routeFamiliesRemainStable() {
        assertThat(SystemModeMetrics.routeFamily("/api/v1/auth/login")).isEqualTo("auth");
        assertThat(SystemModeMetrics.routeFamily("/api/v1/users/me")).isEqualTo("users");
        assertThat(SystemModeMetrics.routeFamily("/oauth2/authorization/google")).isEqualTo("oauth");
        assertThat(SystemModeMetrics.routeFamily("/unmatched/value")).isEqualTo("other");
    }
}
