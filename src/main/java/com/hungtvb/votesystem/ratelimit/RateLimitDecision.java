package com.hungtvb.votesystem.ratelimit;

public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {
    static RateLimitDecision permit() {
        return new RateLimitDecision(true, 0);
    }

    static RateLimitDecision deny(long retryAfterSeconds) {
        return new RateLimitDecision(false, Math.max(1, retryAfterSeconds));
    }
}
