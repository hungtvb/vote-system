package com.hungtvb.votesystem.ranking;

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

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RankingService {
    private static final int FILTER_SCAN_CHUNK_SIZE = 200;

    private final RedisRankingRepository rankingRepository;
    private final PostRepository postRepository;
    private final RankingFormula formula;
    private final Clock clock = Clock.systemUTC();
    private final Counter updates;
    private final Counter rebuilds;
    private final Counter fallbacks;

    public RankingService(RedisRankingRepository rankingRepository, PostRepository postRepository,
                          RankingFormula formula, MeterRegistry meterRegistry) {
        this.rankingRepository = rankingRepository;
        this.postRepository = postRepository;
        this.formula = formula;
        this.updates = meterRegistry.counter("vote.ranking.operations", "result", "update");
        this.rebuilds = meterRegistry.counter("vote.ranking.operations", "result", "rebuild");
        this.fallbacks = meterRegistry.counter("vote.ranking.operations", "result", "fallback");
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
                post -> rankingRepository.upsert(
                        post.getId(),
                        post.getVoteScore(),
                        formula.score(post.getVoteScore(), post.getCreatedAt(), now),
                        post.getCreatedAt(),
                        now
                ),
                () -> rankingRepository.remove(postId, now)
        );
        updates.increment();
    }

    public Page<Post> list(FeedType feed, Pageable pageable) {
        return list(feed, pageable, PostFilter.empty());
    }

    public Page<Post> list(FeedType feed, Pageable pageable, PostFilter filter) {
        if (feed == FeedType.LATEST || feed == FeedType.MINE) {
            return databasePage(pageable, filter);
        }

        Instant now = clock.instant();
        try {
            ensureRankings(feed, now);
            if (filter.isEmpty()) {
                return rankedPage(feed, pageable, now);
            }
            return filteredRankedPage(feed, pageable, filter, now);
        } catch (DataAccessException exception) {
            fallbacks.increment();
            return databasePage(pageable, filter);
        }
    }

    private Page<Post> databasePage(Pageable pageable, PostFilter filter) {
        PageRequest latest = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
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
            if (ids.isEmpty()) {
                break;
            }

            Map<UUID, Post> visibleById = postRepository.findAll(
                            visibleForFeed.and(PostSpecifications.idIn(ids)))
                    .stream()
                    .collect(Collectors.toMap(Post::getId, Function.identity()));

            for (UUID id : ids) {
                Post post = visibleById.get(id);
                if (post == null) {
                    continue;
                }
                if (visibleIndex >= requestedStart && visibleIndex < requestedEnd) {
                    content.add(post);
                }
                visibleIndex += 1;
                if (visibleIndex >= requestedEnd) {
                    break outer;
                }
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
            if (ids.isEmpty()) {
                break;
            }

            Map<UUID, Post> matchesById = postRepository.findAll(
                            baseSpecification.and(PostSpecifications.idIn(ids)))
                    .stream()
                    .collect(Collectors.toMap(Post::getId, Function.identity()));

            for (UUID id : ids) {
                Post post = matchesById.get(id);
                if (post == null) {
                    continue;
                }
                if (matchedTotal >= requestedStart && matchedTotal < requestedEnd) {
                    content.add(post);
                }
                matchedTotal += 1;
            }
        }

        return new PageImpl<>(content, pageable, matchedTotal);
    }

    private void ensureRankings(FeedType feed, Instant now) {
        if (rankingRepository.isEmpty(feed, now)
                && postRepository.count(PostSpecifications.publiclyVisible().and(rankedWindow(feed, now))) > 0) {
            rebuild();
        }
    }

    private Specification<Post> rankedWindow(FeedType feed, Instant now) {
        return switch (feed) {
            case HOT -> (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
            case TOP_DAY -> PostSpecifications.createdAtOnOrAfter(
                    LocalDate.ofInstant(now, ZoneOffset.UTC)
                            .atStartOfDay(ZoneOffset.UTC)
                            .toInstant());
            case TOP_WEEK -> PostSpecifications.createdAtOnOrAfter(
                    LocalDate.ofInstant(now, ZoneOffset.UTC)
                            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                            .atStartOfDay(ZoneOffset.UTC)
                            .toInstant());
            case LATEST, MINE -> throw new IllegalArgumentException(feed + " is database-backed");
        };
    }

    public void rebuild() {
        Instant now = clock.instant();
        rankingRepository.clearCurrent(now);
        for (Post post : postRepository.findAll(PostSpecifications.publiclyVisible())) {
            rankingRepository.upsert(post.getId(), post.getVoteScore(),
                    formula.score(post.getVoteScore(), post.getCreatedAt(), now), post.getCreatedAt(), now);
        }
        rebuilds.increment();
    }
}
