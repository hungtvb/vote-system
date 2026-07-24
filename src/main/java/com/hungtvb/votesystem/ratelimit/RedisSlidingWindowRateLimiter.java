package com.hungtvb.votesystem.ratelimit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
public class RedisSlidingWindowRateLimiter {
    private static final DefaultRedisScript<List> SCRIPT = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local member = ARGV[4]
            local cutoff = now - window
            redis.call('ZREMRANGEBYSCORE', key, 0, cutoff)
            local count = redis.call('ZCARD', key)
            if count >= limit then
              local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
              local retry = window
              if oldest[2] then retry = math.max(1, window - (now - tonumber(oldest[2]))) end
              redis.call('PEXPIRE', key, window)
              return {0, retry}
            end
            redis.call('ZADD', key, now, member)
            redis.call('PEXPIRE', key, window)
            return {1, 0}
            """, List.class);

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;
    private final Clock clock;
    private final Counter allowed;
    private final Counter rejected;
    private final Counter errors;

    public RedisSlidingWindowRateLimiter(StringRedisTemplate redisTemplate,
                                         RateLimitProperties properties,
                                         MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.clock = Clock.systemUTC();
        this.allowed = meterRegistry.counter("vote.rate_limit.requests", "result", "allowed");
        this.rejected = meterRegistry.counter("vote.rate_limit.requests", "result", "rejected");
        this.errors = meterRegistry.counter("vote.rate_limit.requests", "result", "error");
    }

    public RateLimitDecision check(String rule, String subject, RateLimitProperties.Policy policy) {
        if (!properties.enabled()) {
            return RateLimitDecision.permit();
        }

        long now = clock.millis();
        long windowMillis = policy.window().toMillis();
        String key = "rate:" + rule + ":" + subject;
        String member = now + ":" + UUID.randomUUID();

        try {
            List<?> result = redisTemplate.execute(
                    SCRIPT,
                    List.of(key),
                    Long.toString(now),
                    Long.toString(windowMillis),
                    Integer.toString(policy.limit()),
                    member
            );
            if (result == null || result.size() < 2) {
                throw new IllegalStateException("Redis rate limiter returned an invalid result");
            }
            boolean isAllowed = ((Number) result.get(0)).longValue() == 1;
            if (isAllowed) {
                allowed.increment();
                return RateLimitDecision.permit();
            }
            rejected.increment();
            long retryMillis = ((Number) result.get(1)).longValue();
            return RateLimitDecision.deny(Duration.ofMillis(retryMillis).toSeconds() + 1);
        } catch (RedisConnectionFailureException | IllegalStateException exception) {
            errors.increment();
            if (properties.failOpen()) {
                return RateLimitDecision.permit();
            }
            throw exception;
        }
    }
}
