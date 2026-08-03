package com.hungtvb.votesystem.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        boolean failOpen,
        Policy login,
        Policy register,
        Policy refresh,
        Policy socialStart,
        Policy createPost,
        Policy vote,
        Policy profileUpdate,
        Policy reportCreate,
        Policy sessionManagement,
        Policy commentCreate,
        Policy commentEdit
) {
    public record Policy(int limit, Duration window) {
    }
}
