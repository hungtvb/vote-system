package com.hungtvb.votesystem.ranking;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

import java.time.Duration;

@ConfigurationProperties("app.ranking.rebuild")
public record RankingRebuildProperties(
        int batchSize,
        Duration lockTtl,
        Duration lockRenewInterval
) {
    public RankingRebuildProperties {
        Assert.isTrue(batchSize > 0 && batchSize <= 5_000,
                "Ranking rebuild batch size must be between 1 and 5000");
        Assert.notNull(lockTtl, "Ranking rebuild lock TTL is required");
        Assert.notNull(lockRenewInterval, "Ranking rebuild lock renewal interval is required");
        Assert.isTrue(!lockTtl.isNegative() && !lockTtl.isZero(),
                "Ranking rebuild lock TTL must be positive");
        Assert.isTrue(!lockRenewInterval.isNegative() && !lockRenewInterval.isZero(),
                "Ranking rebuild lock renewal interval must be positive");
        Assert.isTrue(lockRenewInterval.compareTo(lockTtl) < 0,
                "Ranking rebuild lock renewal interval must be shorter than the lock TTL");
    }
}
