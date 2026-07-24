package com.hungtvb.votesystem.ranking;

import com.hungtvb.votesystem.post.Post;
import com.hungtvb.votesystem.post.PostRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RankingService {
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

    public Page<Post> list(FeedType feed, Pageable pageable) {
        if (feed == FeedType.LATEST) {
            return postRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        Instant now = clock.instant();
        try {
            if (rankingRepository.isEmpty(feed, now) && postRepository.count() > 0) {
                rebuild();
            }
            List<UUID> ids = rankingRepository.range(feed, (int) pageable.getOffset(), pageable.getPageSize(), now);
            Map<UUID, Post> byId = postRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(Post::getId, Function.identity()));
            List<Post> ordered = ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
            return new PageImpl<>(ordered, pageable, rankingRepository.count(feed, now));
        } catch (DataAccessException exception) {
            fallbacks.increment();
            return postRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
    }

    public void rebuild() {
        Instant now = clock.instant();
        rankingRepository.clearCurrent(now);
        for (Post post : postRepository.findAll()) {
            rankingRepository.upsert(post.getId(), post.getVoteScore(),
                    formula.score(post.getVoteScore(), post.getCreatedAt(), now), post.getCreatedAt(), now);
        }
        rebuilds.increment();
    }
}
