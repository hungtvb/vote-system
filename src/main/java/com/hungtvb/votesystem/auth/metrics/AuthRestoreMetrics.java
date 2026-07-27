package com.hungtvb.votesystem.auth.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
public class AuthRestoreMetrics {
    public static final String OPERATION_REFRESH = "refresh";
    public static final String OPERATION_PROFILE = "profile";

    private static final double[] PERCENTILES = {0.5, 0.95, 0.99};

    private final MeterRegistry meterRegistry;

    public AuthRestoreMetrics(MeterRegistry meterRegistry) {
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

    public RestoreSample startTotal(String operation) {
        return new RestoreSample(operation, Timer.start(meterRegistry));
    }

    public long stopTotal(RestoreSample sample, String outcome) {
        long nanoseconds = sample.sample().stop(totalTimer(sample.operation(), outcome));
        return TimeUnit.NANOSECONDS.toMillis(nanoseconds);
    }

    private Timer stageTimer(String operation, String stage, String result) {
        return Timer.builder("vote.auth.restore.stage")
                .description("Application time spent in one bounded auth restore stage")
                .tags("operation", operation, "stage", stage, "result", result)
                .publishPercentiles(PERCENTILES)
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    private Timer totalTimer(String operation, String outcome) {
        return Timer.builder("vote.auth.restore.total")
                .description("Application time spent restoring authentication state")
                .tags("operation", operation, "outcome", outcome)
                .publishPercentiles(PERCENTILES)
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    public record RestoreSample(String operation, Timer.Sample sample) {
    }
}
