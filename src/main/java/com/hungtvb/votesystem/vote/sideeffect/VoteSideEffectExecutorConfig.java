package com.hungtvb.votesystem.vote.sideeffect;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

import static com.hungtvb.votesystem.vote.sideeffect.VoteSideEffectDispatcher.Effect.RANKING;
import static com.hungtvb.votesystem.vote.sideeffect.VoteSideEffectDispatcher.Effect.SSE;

@Configuration
@EnableConfigurationProperties(VoteSideEffectProperties.class)
public class VoteSideEffectExecutorConfig {

    @Bean(name = "voteRankingExecutor")
    ThreadPoolTaskExecutor voteRankingExecutor(VoteSideEffectProperties properties,
                                               MeterRegistry meterRegistry) {
        return singleThreadExecutor("vote-ranking-", RANKING, properties, meterRegistry);
    }

    @Bean(name = "voteSseExecutor")
    ThreadPoolTaskExecutor voteSseExecutor(VoteSideEffectProperties properties,
                                           MeterRegistry meterRegistry) {
        return singleThreadExecutor("vote-sse-", SSE, properties, meterRegistry);
    }

    private ThreadPoolTaskExecutor singleThreadExecutor(
            String threadNamePrefix,
            VoteSideEffectDispatcher.Effect effect,
            VoteSideEffectProperties properties,
            MeterRegistry meterRegistry) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(properties.shutdownWaitSeconds());
        executor.setRejectedExecutionHandler((task, pool) -> enqueueWithoutReordering(
                task, pool, effect, meterRegistry));
        executor.initialize();
        return executor;
    }

    private void enqueueWithoutReordering(
            Runnable task,
            ThreadPoolExecutor pool,
            VoteSideEffectDispatcher.Effect effect,
            MeterRegistry meterRegistry) {
        meterRegistry.counter(
                "vote.side_effect.execution",
                "effect", effect.tag(),
                "result", "blocked_enqueue"
        ).increment();

        if (pool.isShutdown()) {
            meterRegistry.counter(
                    "vote.side_effect.execution",
                    "effect", effect.tag(),
                    "result", "caller_runs"
            ).increment();
            task.run();
            return;
        }

        try {
            pool.getQueue().put(task);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            meterRegistry.counter(
                    "vote.side_effect.execution",
                    "effect", effect.tag(),
                    "result", "interrupted_caller_runs"
            ).increment();
            task.run();
        }
    }
}
