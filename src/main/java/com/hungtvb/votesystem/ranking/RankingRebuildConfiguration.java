package com.hungtvb.votesystem.ranking;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableConfigurationProperties(RankingRebuildProperties.class)
public class RankingRebuildConfiguration {

    @Bean(name = "rankingRebuildTaskScheduler")
    ThreadPoolTaskScheduler rankingRebuildTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ranking-rebuild-lock-");
        scheduler.setDaemon(true);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }
}
