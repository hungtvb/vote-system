package com.hungtvb.votesystem.vote.stream;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterBinder;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class VoteStreamMetricsBinder implements MeterBinder {
    private final BallotVoteStreamService streamService;

    public VoteStreamMetricsBinder(BallotVoteStreamService streamService) {
        this.streamService = streamService;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("vote.sse.subscribers.active", streamService,
                        BallotVoteStreamService::totalActiveSubscribers)
                .description("Current number of active SSE subscribers across all ballots")
                .register(registry);
    }
}
