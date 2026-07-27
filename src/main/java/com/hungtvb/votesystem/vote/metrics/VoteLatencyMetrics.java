package com.hungtvb.votesystem.vote.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
public class VoteLatencyMetrics {
    public static final String OPERATION_CAST = "cast";
    public static final String OPERATION_REMOVE = "remove";
    public static final String OPERATION_POST_COMMIT = "post_commit";

    private static final double[] PERCENTILES = {0.5, 0.95, 0.99};

    private final MeterRegistry meterRegistry;

    public VoteLatencyMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public <T> T timeStage(String operation, String stage, Supplier<T> action) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            T result = action.get();
            sample.stop(stageTimer(operation, stage, "success"));
            return result;
        } catch (RuntimeException exception) {
            sample.stop(stageTimer(operation, stage, "error"));
            throw exception;
        }
    }

    public void timeStage(String operation, String stage, Runnable action) {
        timeStage(operation, stage, () -> {
            action.run();
            return null;
        });
    }

    public VoteSample startTotal(String operation) {
        return new VoteSample(operation, Timer.start(meterRegistry));
    }

    public long stopTotal(VoteSample sample, String outcome) {
        long nanoseconds = sample.sample().stop(totalTimer(sample.operation(), outcome));
        return TimeUnit.NANOSECONDS.toMillis(nanoseconds);
    }

    private Timer stageTimer(String operation, String stage, String result) {
        return Timer.builder("vote.operation.stage")
                .description("Application time spent in one bounded vote operation stage")
                .tags("operation", operation, "stage", stage, "result", result)
                .publishPercentiles(PERCENTILES)
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    private Timer totalTimer(String operation, String outcome) {
        return Timer.builder("vote.operation.total")
                .description("Total application time spent handling a vote request")
                .tags("operation", operation, "outcome", outcome)
                .publishPercentiles(PERCENTILES)
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    public record VoteSample(String operation, Timer.Sample sample) {
    }
}
