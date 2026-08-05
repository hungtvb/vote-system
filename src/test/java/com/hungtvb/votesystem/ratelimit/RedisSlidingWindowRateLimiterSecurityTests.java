package com.hungtvb.votesystem.ratelimit;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisSlidingWindowRateLimiterSecurityTests {

    @Test
    void authenticationRuleFailsClosedWhenRedisIsUnavailable() {
        RedisSlidingWindowRateLimiter limiter = limiter(true);

        assertThatThrownBy(() -> limiter.check("login", "127.0.0.1", policy()))
                .isInstanceOf(RedisConnectionFailureException.class);
    }

    @Test
    void nonAuthenticationRuleMayFailOpenWhenConfigured() {
        RedisSlidingWindowRateLimiter limiter = limiter(true);

        RateLimitDecision decision = limiter.check("vote", "user-id", policy());

        assertThat(decision.allowed()).isTrue();
    }

    private RedisSlidingWindowRateLimiter limiter(boolean failOpen) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), any(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("redis unavailable"));
        return new RedisSlidingWindowRateLimiter(
                redis,
                properties(failOpen),
                new SimpleMeterRegistry()
        );
    }

    private RateLimitProperties properties(boolean failOpen) {
        RateLimitProperties.Policy policy = policy();
        return new RateLimitProperties(
                true,
                failOpen,
                policy,
                policy,
                policy,
                policy,
                policy,
                policy,
                policy,
                policy,
                policy,
                policy,
                policy,
                policy
        );
    }

    private RateLimitProperties.Policy policy() {
        return new RateLimitProperties.Policy(5, Duration.ofMinutes(1));
    }
}
