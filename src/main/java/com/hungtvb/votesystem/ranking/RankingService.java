package com.hungtvb.votesystem.ranking;

import com.hungtvb.votesystem.common.error.ConflictException;
import com.hungtvb.votesystem.post.Post;
import com.hungtvb.votesystem.post.PostFilter;
import com.hungtvb.votesystem.post.PostRepository;
import com.hungtvb.votesystem.post.PostSpecifications;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RankingService {
    private static final int FILTER_SCAN_CHUNK_SIZE = 200;
    private static final int MAX_REBUILD_ATTEMPTS = 3;
    private static final Duration REBUILD_LOCK_TTL = Duration.ofMinutes(5);

    private final RedisRankingRepository rankingRepository;
    private final PostRepository postRepository;
    private final RankingRevisionStore revisionStore;
    private final RankingFormula formula;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock = Clock.systemUTC();
    private final Counter updates;
    private final Counter rebuilds;
    private final Counter fallbacks;
    private final Counter rebuildRequested;
    private final Counter rebuildSucceeded;
    private final Counter rebuildFailed;
    private final Counter rebuildRejected;
    private final Counter rebuildRetried;

    public RankingService(RedisRankingRepository rankingRepository,
                          PostRepository postRepository,
                          RankingRevisionStore revisionStore,
                          RankingFormula formula,
                          MeterRegistry meterRegistry,
                          PlatformTransactionManager transactionManager) {
        this.rankingRepository = rankingRepository;
        this.postRepository = postRepository;
        this.revisionStore = revisionStore;
        this.formula = formula;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.updates = meterRegistry.counter("vote.ranking.operations", "result", "update");
        this.rebuilds = meterRegistry.counter("vote.ranking.operations", "result", "rebuild");
        this.fallbacks = meterRegistry.counter("vote.ranking.operations", "result", "fallback");
        this.rebuildRequested = meterRegistry.counter("vote.ranking.rebuild", "result", "requested");
        this.rebuildSucceeded = meterRegistry.counter("vote.ranking.rebuild", "result", "succeeded");
        this.rebuildFailed = meterRegistry.counter("vote.ranking.rebuild", "result", "failed");
        this.rebuildRejected = meterRegistry.counter("vote.ranking.rebuild", "result", "rejected");
        this.rebuildRetried = meterRegistry.counter("vote.ranking.rebuild", "result", "retried");
    }

    public void apply(RankingChangedEvent event) {
        Instant now = clock.instant();
        if (event.deleted()) {
            rankingRepository.remove(event.postId(), now);
        } else {
            rankingRepository.upsert(event.postId(), event.voteScore(),
                    formula.score(event.voteScore(), event.createdAt(), now), event.createdAt(), now);
        }
        updates.increment();
    }

    public void applyLatest(UUID postId) {
        Instant now = clock.instant();
        postRepository.findById(postId).filter(Post::isPubliclyVisible).ifPresentOrElse(
                post -> rankingRepository.upsert(post.getId(), post.getVoteScore(),
                        formula.score(post.getVoteScore(), post.getCreatedAt(), now), post.getCreatedAt(), now),
                () -> rankingRepository.remove(postId, now));
        updates.increment();
    }

    public Page<Post> list(FeedType feed, Pageable pageable) {
        return list(feed, pageable, PostFilter.empty());
    }

    public Page<Post> list(FeedType feed, Pageable pageable, PostFilter filter) {
        if (feed == FeedType.LATEST || feed == FeedType.MINE) return databasePage(pageable, filter);
        Instant now = clock.instant();
        try {
            ensureRankings(feed, now);
            return filter.isEmpty()
                    ? rankedPage(feed, pageable, now)
                    : filteredRankedPage(feed, pageable, filter, now);
        } catch (DataAccessException exception) {
            fallbacks.increment();
            return databasePage(pageable, filter);
        }
    }

    public RankingStatusSnapshot status() {
        Instant now = clock.instant();
        long visible = postRepository.count(PostSpecifications.publiclyVisible());
        long day = postRepository.count(PostSpecifications.publiclyVisible().and(rankedWindow(FeedType.TOP_DAY, now)));
        long week = postRepository.count(PostSpecifications.publiclyVisible().and(rankedWindow(FeedType.TOP_WEEK, now)));
        try {
            RedisRankingRepository.RankingCounts counts = rankingRepository.currentCounts(now);
            RedisRankingRepository.RankingMetadata metadata = rankingRepository.metadata();
            boolean rebuilding = rankingRepository.isRebuildInProgress();
            boolean matches = counts.hot() == visible && counts.day() == day && counts.week() == week;
            boolean currentWindow = !rankingRepository.needsWindowRebuild(now);
            RankingAvailability availability = rebuilding
                    ? RankingAvailability.REBUILDING
                    : matches && currentWindow ? RankingAvailability.HEALTHY : RankingAvailability.STALE;
            return new RankingStatusSnapshot(availability, visible, day, week,
                    counts.hot(), counts.day(), counts.week(), metadata.generation(),
                    metadata.publishedAt(), rebuilding);
        } catch (DataAccessException exception) {
            return new RankingStatusSnapshot(RankingAvailability.UNAVAILABLE, visible, day, week,
                    null, null, null, null, null, false);
        }
    }

    public RankingRebuildResult rebuild() {
        return rebuild(preview -> { });
    }

    public RankingRebuildResult rebuild(Consumer<RankingRebuildPreview> beforePublish) {
        Objects.requireNonNull(beforePublish, "beforePublish is required");
        rebuildRequested.increment();
        String token = UUID.randomUUID().toString();
        if (!rankingRepository.tryAcquireRebuildLock(token, REBUILD_LOCK_TTL)) {
            rebuildRejected.increment();
            throw new ConflictException("A ranking rebuild is already in progress");
        }

        try {
            for (int attempt = 1; attempt <= MAX_REBUILD_ATTEMPTS; attempt++) {
                RankingRebuildPreview preview = prepareRebuildAttempt();
                AtomicReference<RankingRebuildResult> published = new AtomicReference<>();
                try {
                    RankingRebuildResult result = transactionTemplate.execute(status -> {
                        long currentRevision = revisionStore.lockAndRead();
                        if (currentRevision != preview.sourceRevision()) {
                            throw new RankingRevisionChangedException();
                        }

                        beforePublish.accept(preview);
                        RedisRankingRepository.PublishState publishState = rankingRepository.publishGeneration(
                                preview.generation(), preview.stagedCounts(), token, preview.preparedAt());
                        RankingRebuildResult committed = new RankingRebuildResult(
                                token,
                                preview.generation(),
                                publishState,
                                preview.previousCounts(),
                                preview.stagedCounts(),
                                preview.visiblePostCount(),
                                preview.sourceRevision(),
                                preview.preparedAt()
                        );
                        published.set(committed);
                        return committed;
                    });
                    if (result == null) {
                        throw new IllegalStateException("Ranking rebuild transaction returned no result");
                    }
                    rankingRepository.releaseRebuildLock(token);
                    rebuilds.increment();
                    rebuildSucceeded.increment();
                    return result;
                } catch (RankingRevisionChangedException exception) {
                    rankingRepository.discardGeneration(preview.generation());
                    if (attempt == MAX_REBUILD_ATTEMPTS) {
                        throw new ConflictException("Ranking changed repeatedly during rebuild; retry later");
                    }
                    rebuildRetried.increment();
                } catch (RuntimeException exception) {
                    rollbackPublishedOrDiscard(preview, published.get(), token, exception);
                    throw exception;
                }
            }
            throw new IllegalStateException("Ranking rebuild retry loop ended unexpectedly");
        } catch (RuntimeException exception) {
            rebuildFailed.increment();
            throw exception;
        } finally {
            rankingRepository.releaseRebuildLock(token);
        }
    }

    private RankingRebuildPreview prepareRebuildAttempt() {
        long sourceRevision = revisionStore.current();
        RedisRankingRepository.RankingGeneration generation =
                rankingRepository.createGeneration(UUID.randomUUID().toString());
        try {
            Instant now = clock.instant();
            RedisRankingRepository.RankingCounts previousCounts = rankingRepository.currentCounts(now);
            RedisRankingRepository.RankingMetadata previousMetadata = rankingRepository.metadata();
            List<Post> visiblePosts = postRepository.findAll(PostSpecifications.publiclyVisible());
            for (Post post : visiblePosts) {
                rankingRepository.stageUpsert(generation, post.getId(), post.getVoteScore(),
                        formula.score(post.getVoteScore(), post.getCreatedAt(), now), post.getCreatedAt(), now);
            }
            RedisRankingRepository.RankingCounts staged = rankingRepository.generationCounts(generation);
            long expectedDay = visiblePosts.stream().filter(post -> isSameUtcDay(post.getCreatedAt(), now)).count();
            long expectedWeek = visiblePosts.stream().filter(post -> isSameUtcWeek(post.getCreatedAt(), now)).count();
            RedisRankingRepository.RankingCounts expected =
                    new RedisRankingRepository.RankingCounts(visiblePosts.size(), expectedDay, expectedWeek);
            if (!expected.equals(staged)) {
                throw new IllegalStateException("Staged ranking generation failed verification");
            }
            return new RankingRebuildPreview(generation, previousMetadata, previousCounts, expected,
                    visiblePosts.size(), sourceRevision, now);
        } catch (RuntimeException exception) {
            rankingRepository.discardGeneration(generation);
            throw exception;
        }
    }

    private void rollbackPublishedOrDiscard(RankingRebuildPreview preview,
                                            RankingRebuildResult published,
                                            String token,
                                            RuntimeException original) {
        try {
            if (published == null) {
                rankingRepository.discardGeneration(preview.generation());
            } else {
                rankingRepository.rollbackGeneration(
                        published.generation(), published.publishState(), token);
            }
        } catch (RuntimeException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private Page<Post> databasePage(Pageable pageable, PostFilter filter) {
        PageRequest latest = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return postRepository.findAll(PostSpecifications.matches(filter), latest);
    }

    private Page<Post> rankedPage(FeedType feed, Pageable pageable, Instant now) {
        Specification<Post> visibleForFeed = PostSpecifications.publiclyVisible().and(rankedWindow(feed, now));
        long visibleTotal = postRepository.count(visibleForFeed);
        if (visibleTotal == 0 || pageable.getOffset() >= visibleTotal) {
            return new PageImpl<>(List.of(), pageable, visibleTotal);
        }
        long rawTotal = rankingRepository.count(feed, now);
        long requestedStart = pageable.getOffset();
        long requestedEnd = requestedStart + pageable.getPageSize();
        long visibleIndex = 0;
        List<Post> content = new ArrayList<>(pageable.getPageSize());
        outer:
        for (long rawOffset = 0; rawOffset < rawTotal; rawOffset += FILTER_SCAN_CHUNK_SIZE) {
            int chunkSize = (int) Math.min(FILTER_SCAN_CHUNK_SIZE, rawTotal - rawOffset);
            List<UUID> ids = rankingRepository.range(feed, rawOffset, chunkSize, now);
            if (ids.isEmpty()) break;
            Map<UUID, Post> visibleById = postRepository.findAll(
                            visibleForFeed.and(PostSpecifications.idIn(ids))).stream()
                    .collect(Collectors.toMap(Post::getId, Function.identity()));
            for (UUID id : ids) {
                Post post = visibleById.get(id);
                if (post == null) continue;
                if (visibleIndex >= requestedStart && visibleIndex < requestedEnd) content.add(post);
                visibleIndex += 1;
                if (visibleIndex >= requestedEnd) break outer;
            }
        }
        return new PageImpl<>(content, pageable, visibleTotal);
    }

    private Page<Post> filteredRankedPage(FeedType feed, Pageable pageable, PostFilter filter, Instant now) {
        long rawTotal = rankingRepository.count(feed, now);
        long requestedStart = pageable.getOffset();
        long requestedEnd = requestedStart + pageable.getPageSize();
        long matchedTotal = 0;
        List<Post> content = new ArrayList<>(pageable.getPageSize());
        Specification<Post> baseSpecification = PostSpecifications.matches(filter).and(rankedWindow(feed, now));
        for (long rawOffset = 0; rawOffset < rawTotal; rawOffset += FILTER_SCAN_CHUNK_SIZE) {
            int chunkSize = (int) Math.min(FILTER_SCAN_CHUNK_SIZE, rawTotal - rawOffset);
            List<UUID> ids = rankingRepository.range(feed, rawOffset, chunkSize, now);
            if (ids.isEmpty()) break;
            Map<UUID, Post> matchesById = postRepository.findAll(
                            baseSpecification.and(PostSpecifications.idIn(ids))).stream()
                    .collect(Collectors.toMap(Post::getId, Function.identity()));
            for (UUID id : ids) {
                Post post = matchesById.get(id);
                if (post == null) continue;
                if (matchedTotal >= requestedStart && matchedTotal < requestedEnd) content.add(post);
                matchedTotal += 1;
            }
        }
        return new PageImpl<>(content, pageable, matchedTotal);
    }

    private void ensureRankings(FeedType feed, Instant now) {
        boolean missing = rankingRepository.isEmpty(feed, now)
                && postRepository.count(PostSpecifications.publiclyVisible().and(rankedWindow(feed, now))) > 0;
        if (!rankingRepository.needsWindowRebuild(now) && !missing) return;
        try {
            rebuild();
        } catch (ConflictException ignored) {
            // Another request is already rebuilding or writes prevented a stable snapshot.
        }
    }

    private Specification<Post> rankedWindow(FeedType feed, Instant now) {
        return switch (feed) {
            case HOT -> (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
            case TOP_DAY -> PostSpecifications.createdAtOnOrAfter(
                    LocalDate.ofInstant(now, ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant());
            case TOP_WEEK -> PostSpecifications.createdAtOnOrAfter(
                    LocalDate.ofInstant(now, ZoneOffset.UTC)
                            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                            .atStartOfDay(ZoneOffset.UTC).toInstant());
            case LATEST, MINE -> throw new IllegalArgumentException(feed + " is database-backed");
        };
    }

    private boolean isSameUtcDay(Instant first, Instant second) {
        return LocalDate.ofInstant(first, ZoneOffset.UTC).equals(LocalDate.ofInstant(second, ZoneOffset.UTC));
    }

    private boolean isSameUtcWeek(Instant first, Instant second) {
        LocalDate a = LocalDate.ofInstant(first, ZoneOffset.UTC)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate b = LocalDate.ofInstant(second, ZoneOffset.UTC)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return a.equals(b);
    }

    private static final class RankingRevisionChangedException extends RuntimeException {
    }
}
