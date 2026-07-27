package com.hungtvb.votesystem.vote.sideeffect;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties("app.vote-side-effects")
public record VoteSideEffectProperties(
        int queueCapacity,
        int shutdownWaitSeconds
) {
    public VoteSideEffectProperties {
        Assert.isTrue(queueCapacity > 0, "Vote side-effect queue capacity must be positive");
        Assert.isTrue(shutdownWaitSeconds > 0, "Vote side-effect shutdown wait must be positive");
    }
}
