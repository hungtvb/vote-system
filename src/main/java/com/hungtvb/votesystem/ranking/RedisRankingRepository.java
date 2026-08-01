package com.hungtvb.votesystem.ranking;

import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Repository
public class RedisRankingRepository {
    private static final String LEGACY_HOT_KEY = "feed:hot";
    private static final String ACTIVE_GENERATION_KEY = "feed:{ranking}:active-generation";
    private static final String METADATA_KEY = "feed:{ranking}:metadata";
    private static final String REBUILD_LOCK_KEY = "feed:{ranking}:rebuild-lock";
    private static final Duration STAGING_TTL = Duration.ofMinutes(15);
    private static final Duration OLD_GENERATION_TTL = Duration.ofHours(1);
    private static final String STAGING_SENTINEL = "__ranking_staging__";

    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private static final DefaultRedisScript<Long> RENEW_LOCK_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('PEXPIRE', KEYS[1], tonumber(ARGV[2]))
            end
            return 0
            """, Long.class);

    private static final DefaultRedisScript<Long> OWNS_LOCK_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then return 1 end
            return 0
            """, Long.class);

    private static final DefaultRedisScript<Long> PUBLISH_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return -1 end
            if redis.call('ZCARD', KEYS[4]) ~= tonumber(ARGV[6]) then return -2 end
            if redis.call('ZCARD', KEYS[5]) ~= tonumber(ARGV[7]) then return -2 end
            if redis.call('ZCARD', KEYS[6]) ~= tonumber(ARGV[8]) then return -2 end

            redis.call('PERSIST', KEYS[4])
            redis.call('PERSIST', KEYS[5])
            redis.call('PERSIST', KEYS[6])
            redis.call('SET', KEYS[2], ARGV[2])
            redis.call('HSET', KEYS[3],
              'generation', ARGV[2],
              'published_at', ARGV[3],
              'window_day', ARGV[4],
              'window_week', ARGV[5])

            if ARGV[9] ~= '' and ARGV[9] ~= ARGV[2] then
              redis.call('EXPIRE', KEYS[7], tonumber(ARGV[10]))
              redis.call('EXPIRE', KEYS[8], tonumber(ARGV[10]))
              redis.call('EXPIRE', KEYS[9], tonumber(ARGV[10]))
            end
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return -1 end
            if redis.call('GET', KEYS[2]) ~= ARGV[2] then return -2 end

            if ARGV[3] == '' then
              redis.call('DEL', KEYS[2])
              redis.call('DEL', KEYS[3])
            else
              redis.call('SET', KEYS[2], ARGV[3])
              redis.call('HSET', KEYS[3],
                'generation', ARGV[3],
                'published_at', ARGV[4],
                'window_day', ARGV[5],
                'window_week', ARGV[6])
              redis.call('PERSIST', KEYS[7])
              redis.call('PERSIST', KEYS[8])
              redis.call('PERSIST', KEYS[9])
            end

            redis.call('EXPIRE', KEYS[4], tonumber(ARGV[7]))
            redis.call('EXPIRE', KEYS[5], tonumber(ARGV[7]))
            redis.call('EXPIRE', KEYS[6], tonumber(ARGV[7]))
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;

    public RedisRankingRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void upsert(UUID postId, long voteScore, double hotScore, Instant createdAt, Instant now) {
        String generation = activeGeneration();
        String member = postId.toString();
        redis.opsForZSet().add(key(generation, FeedType.HOT, now), member, hotScore);
        if (isSameUtcDay(createdAt, now)) {
            redis.opsForZSet().add(key(generation, FeedType.TOP_DAY, now), member, voteScore);
        } else {
            redis.opsForZSet().remove(key(generation, FeedType.TOP_DAY, now), member);
        }
        if (isSameUtcWeek(createdAt, now)) {
            redis.opsForZSet().add(key(generation, FeedType.TOP_WEEK, now), member, voteScore);
        } else {
            redis.opsForZSet().remove(key(generation, FeedType.TOP_WEEK, now), member);
        }
    }

    public void remove(UUID postId, Instant now) {
        String generation = activeGeneration();
        String member = postId.toString();
        redis.opsForZSet().remove(key(generation, FeedType.HOT, now), member);
        redis.opsForZSet().remove(key(generation, FeedType.TOP_DAY, now), member);
        redis.opsForZSet().remove(key(generation, FeedType.TOP_WEEK, now), member);
    }

    public List<UUID> range(FeedType feed, long offset, int size, Instant now) {
        Set<String> members = redis.opsForZSet().reverseRange(key(activeGeneration(), feed, now), offset, offset + size - 1L);
        if (members == null) return List.of();
        return new LinkedHashSet<>(members).stream().map(UUID::fromString).toList();
    }

    public long count(FeedType feed, Instant now) {
        Long size = redis.opsForZSet().zCard(key(activeGeneration(), feed, now));
        return size == null ? 0 : size;
    }

    public boolean isEmpty(FeedType feed, Instant now) {
        return count(feed, now) == 0;
    }

    public RankingCounts currentCounts(Instant now) {
        String generation = activeGeneration();
        return new RankingCounts(
                size(key(generation, FeedType.HOT, now)),
                size(key(generation, FeedType.TOP_DAY, now)),
                size(key(generation, FeedType.TOP_WEEK, now))
        );
    }

    public RankingMetadata metadata() {
        String generation = activeGeneration();
        if (generation == null || generation.isBlank()) return RankingMetadata.empty();
        Map<Object, Object> values = redis.opsForHash().entries(METADATA_KEY);
        return new RankingMetadata(
                generation,
                parseInstant(values.get("published_at")),
                stringValue(values.get("window_day")),
                stringValue(values.get("window_week"))
        );
    }

    public boolean needsWindowRebuild(Instant now) {
        RankingMetadata metadata = metadata();
        return metadata.generation() == null
                || !dayValue(now).equals(metadata.windowDay())
                || !weekValue(now).equals(metadata.windowWeek());
    }

    public boolean tryAcquireRebuildLock(String token, Duration ttl) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(REBUILD_LOCK_KEY, token, ttl));
    }

    public boolean isRebuildInProgress() {
        return Boolean.TRUE.equals(redis.hasKey(REBUILD_LOCK_KEY));
    }

    public boolean renewRebuildLock(String token, Duration ttl) {
        Long renewed = redis.execute(RENEW_LOCK_SCRIPT, List.of(REBUILD_LOCK_KEY),
                token, Long.toString(ttl.toMillis()));
        return renewed != null && renewed == 1L;
    }

    public boolean ownsRebuildLock(String token) {
        Long owned = redis.execute(OWNS_LOCK_SCRIPT, List.of(REBUILD_LOCK_KEY), token);
        return owned != null && owned == 1L;
    }

    public boolean releaseRebuildLock(String token) {
        Long released = redis.execute(RELEASE_LOCK_SCRIPT, List.of(REBUILD_LOCK_KEY), token);
        return released != null && released == 1L;
    }

    public RankingGeneration createGeneration(String generationId) {
        return new RankingGeneration(
                generationId,
                generationKey(generationId, FeedType.HOT),
                generationKey(generationId, FeedType.TOP_DAY),
                generationKey(generationId, FeedType.TOP_WEEK)
        );
    }

    public void initializeGeneration(RankingGeneration generation) {
        redis.executePipelined(new SessionCallback<>() {
            @Override
            @SuppressWarnings({"rawtypes", "unchecked"})
            public Object execute(RedisOperations operations) {
                ZSetOperations<String, String> zSet = operations.opsForZSet();
                for (String key : generation.keys()) {
                    zSet.add(key, STAGING_SENTINEL, 0d);
                    operations.expire(key, STAGING_TTL);
                }
                return null;
            }
        });
    }

    public void stageBatch(RankingGeneration generation, List<RankingEntry> entries) {
        if (entries.isEmpty()) return;

        Set<ZSetOperations.TypedTuple<String>> hot = new LinkedHashSet<>();
        Set<ZSetOperations.TypedTuple<String>> day = new LinkedHashSet<>();
        Set<ZSetOperations.TypedTuple<String>> week = new LinkedHashSet<>();
        for (RankingEntry entry : entries) {
            String member = entry.postId().toString();
            hot.add(new DefaultTypedTuple<>(member, entry.hotScore()));
            if (entry.dayEligible()) day.add(new DefaultTypedTuple<>(member, (double) entry.voteScore()));
            if (entry.weekEligible()) week.add(new DefaultTypedTuple<>(member, (double) entry.voteScore()));
        }

        redis.executePipelined(new SessionCallback<>() {
            @Override
            @SuppressWarnings({"rawtypes", "unchecked"})
            public Object execute(RedisOperations operations) {
                ZSetOperations<String, String> zSet = operations.opsForZSet();
                zSet.add(generation.hotKey(), hot);
                if (!day.isEmpty()) zSet.add(generation.dayKey(), day);
                if (!week.isEmpty()) zSet.add(generation.weekKey(), week);
                return null;
            }
        });
    }

    public void completeGeneration(RankingGeneration generation) {
        redis.executePipelined(new SessionCallback<>() {
            @Override
            @SuppressWarnings({"rawtypes", "unchecked"})
            public Object execute(RedisOperations operations) {
                ZSetOperations<String, String> zSet = operations.opsForZSet();
                for (String key : generation.keys()) {
                    zSet.remove(key, STAGING_SENTINEL);
                }
                return null;
            }
        });
    }

    public RankingCounts generationCounts(RankingGeneration generation) {
        return new RankingCounts(size(generation.hotKey()), size(generation.dayKey()), size(generation.weekKey()));
    }

    public PublishState publishGeneration(RankingGeneration generation, RankingCounts expected,
                                          String token, Instant publishedAt) {
        RankingMetadata previous = metadata();
        RankingGeneration oldGeneration = previous.generation() == null
                ? generation
                : createGeneration(previous.generation());
        Long result = redis.execute(PUBLISH_SCRIPT, List.of(
                        REBUILD_LOCK_KEY,
                        ACTIVE_GENERATION_KEY,
                        METADATA_KEY,
                        generation.hotKey(),
                        generation.dayKey(),
                        generation.weekKey(),
                        oldGeneration.hotKey(),
                        oldGeneration.dayKey(),
                        oldGeneration.weekKey()),
                token,
                generation.id(),
                publishedAt.toString(),
                dayValue(publishedAt),
                weekValue(publishedAt),
                Long.toString(expected.hot()),
                Long.toString(expected.day()),
                Long.toString(expected.week()),
                previous.generation() == null ? "" : previous.generation(),
                Long.toString(OLD_GENERATION_TTL.toSeconds()));
        if (result == null || result != 1L) {
            throw new IllegalStateException("Ranking generation publish was rejected");
        }
        return new PublishState(previous, currentCounts(publishedAt));
    }

    public void rollbackGeneration(RankingGeneration generation, PublishState state, String token) {
        RankingMetadata previous = state.previousMetadata();
        RankingGeneration oldGeneration = previous.generation() == null
                ? generation
                : createGeneration(previous.generation());
        Long result = redis.execute(ROLLBACK_SCRIPT, List.of(
                        REBUILD_LOCK_KEY,
                        ACTIVE_GENERATION_KEY,
                        METADATA_KEY,
                        generation.hotKey(),
                        generation.dayKey(),
                        generation.weekKey(),
                        oldGeneration.hotKey(),
                        oldGeneration.dayKey(),
                        oldGeneration.weekKey()),
                token,
                generation.id(),
                previous.generation() == null ? "" : previous.generation(),
                previous.publishedAt() == null ? "" : previous.publishedAt().toString(),
                previous.windowDay() == null ? "" : previous.windowDay(),
                previous.windowWeek() == null ? "" : previous.windowWeek(),
                Long.toString(STAGING_TTL.toSeconds()));
        if (result == null || result != 1L) {
            throw new IllegalStateException("Ranking generation rollback was rejected");
        }
    }

    public void discardGeneration(RankingGeneration generation) {
        redis.delete(List.of(generation.hotKey(), generation.dayKey(), generation.weekKey()));
    }

    private long size(String key) {
        Long size = redis.opsForZSet().zCard(key);
        return size == null ? 0 : size;
    }

    private String activeGeneration() {
        String generation = redis.opsForValue().get(ACTIVE_GENERATION_KEY);
        return generation == null || generation.isBlank() ? null : generation;
    }

    private String key(String generation, FeedType feed, Instant now) {
        if (generation != null) return generationKey(generation, feed);
        return legacyKey(feed, now);
    }

    private String generationKey(String generation, FeedType feed) {
        String suffix = switch (feed) {
            case HOT -> "hot";
            case TOP_DAY -> "day";
            case TOP_WEEK -> "week";
            case LATEST, MINE -> throw new IllegalArgumentException(feed + " is database-backed");
        };
        return "feed:{ranking}:generation:" + generation + ":" + suffix;
    }

    private String legacyKey(FeedType feed, Instant now) {
        return switch (feed) {
            case HOT -> LEGACY_HOT_KEY;
            case TOP_DAY -> "feed:top:day:" + dayValue(now);
            case TOP_WEEK -> "feed:top:week:" + weekValue(now);
            case LATEST, MINE -> throw new IllegalArgumentException(feed + " is database-backed");
        };
    }

    private String dayValue(Instant now) {
        return LocalDate.ofInstant(now, ZoneOffset.UTC).toString();
    }

    private String weekValue(Instant now) {
        return LocalDate.ofInstant(now, ZoneOffset.UTC)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .toString();
    }

    private boolean isSameUtcDay(Instant first, Instant second) {
        return dayValue(first).equals(dayValue(second));
    }

    private boolean isSameUtcWeek(Instant first, Instant second) {
        return weekValue(first).equals(weekValue(second));
    }

    private Instant parseInstant(Object value) {
        if (value == null) return null;
        try {
            return Instant.parse(value.toString());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    public record RankingGeneration(String id, String hotKey, String dayKey, String weekKey) {
        List<String> keys() {
            return List.of(hotKey, dayKey, weekKey);
        }
    }
    public record RankingEntry(UUID postId, long voteScore, double hotScore,
                               boolean dayEligible, boolean weekEligible) {}
    public record RankingCounts(long hot, long day, long week) {}
    public record RankingMetadata(String generation, Instant publishedAt, String windowDay, String windowWeek) {
        static RankingMetadata empty() { return new RankingMetadata(null, null, null, null); }
    }
    public record PublishState(RankingMetadata previousMetadata, RankingCounts publishedCounts) {}
}
