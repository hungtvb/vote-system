package com.hungtvb.votesystem.ranking;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public class RedisRankingRepository {
    private static final String HOT_KEY = "feed:hot";
    private final StringRedisTemplate redis;

    public RedisRankingRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void upsert(UUID postId, long voteScore, double hotScore, Instant createdAt, Instant now) {
        String member = postId.toString();
        redis.opsForZSet().add(HOT_KEY, member, hotScore);
        if (isSameUtcDay(createdAt, now)) {
            redis.opsForZSet().add(dayKey(now), member, voteScore);
        } else {
            redis.opsForZSet().remove(dayKey(now), member);
        }
        if (isSameUtcWeek(createdAt, now)) {
            redis.opsForZSet().add(weekKey(now), member, voteScore);
        } else {
            redis.opsForZSet().remove(weekKey(now), member);
        }
    }

    public void remove(UUID postId, Instant now) {
        String member = postId.toString();
        redis.opsForZSet().remove(HOT_KEY, member);
        redis.opsForZSet().remove(dayKey(now), member);
        redis.opsForZSet().remove(weekKey(now), member);
    }

    public List<UUID> range(FeedType feed, int offset, int size, Instant now) {
        Set<String> members = redis.opsForZSet().reverseRange(key(feed, now), offset, offset + size - 1L);
        if (members == null) return List.of();
        return new LinkedHashSet<>(members).stream().map(UUID::fromString).toList();
    }

    public long count(FeedType feed, Instant now) {
        Long size = redis.opsForZSet().zCard(key(feed, now));
        return size == null ? 0 : size;
    }

    public boolean isEmpty(FeedType feed, Instant now) {
        return count(feed, now) == 0;
    }

    public void clearCurrent(Instant now) {
        redis.delete(List.of(HOT_KEY, dayKey(now), weekKey(now)));
    }

    private String key(FeedType feed, Instant now) {
        return switch (feed) {
            case HOT -> HOT_KEY;
            case TOP_DAY -> dayKey(now);
            case TOP_WEEK -> weekKey(now);
            case LATEST, MINE -> throw new IllegalArgumentException(feed + " is database-backed");
        };
    }

    private String dayKey(Instant now) {
        return "feed:top:day:" + LocalDate.ofInstant(now, ZoneOffset.UTC);
    }

    private String weekKey(Instant now) {
        LocalDate date = LocalDate.ofInstant(now, ZoneOffset.UTC);
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return "feed:top:week:" + monday;
    }

    private boolean isSameUtcDay(Instant first, Instant second) {
        return LocalDate.ofInstant(first, ZoneOffset.UTC).equals(LocalDate.ofInstant(second, ZoneOffset.UTC));
    }

    private boolean isSameUtcWeek(Instant first, Instant second) {
        LocalDate a = LocalDate.ofInstant(first, ZoneOffset.UTC).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate b = LocalDate.ofInstant(second, ZoneOffset.UTC).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return a.equals(b);
    }
}
