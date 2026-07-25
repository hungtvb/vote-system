package com.hungtvb.votesystem.post;

import com.hungtvb.votesystem.common.error.ConflictException;
import com.hungtvb.votesystem.common.error.ForbiddenException;
import com.hungtvb.votesystem.common.error.ResourceNotFoundException;
import com.hungtvb.votesystem.post.dto.CreatePostRequest;
import com.hungtvb.votesystem.post.dto.PostResponse;
import com.hungtvb.votesystem.post.dto.UpdatePostRequest;
import com.hungtvb.votesystem.ranking.FeedType;
import com.hungtvb.votesystem.ranking.RankingChangedEvent;
import com.hungtvb.votesystem.ranking.RankingService;
import com.hungtvb.votesystem.vote.Vote;
import com.hungtvb.votesystem.vote.VotePolicy;
import com.hungtvb.votesystem.vote.VoteRepository;
import com.hungtvb.votesystem.vote.VoteType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PostService {
    private final PostRepository postRepository;
    private final VoteRepository voteRepository;
    private final RankingService rankingService;
    private final ApplicationEventPublisher eventPublisher;
    private final VotePolicy votePolicy;

    public PostService(PostRepository postRepository,
                       VoteRepository voteRepository,
                       RankingService rankingService,
                       ApplicationEventPublisher eventPublisher,
                       VotePolicy votePolicy) {
        this.postRepository = postRepository;
        this.voteRepository = voteRepository;
        this.rankingService = rankingService;
        this.eventPublisher = eventPublisher;
        this.votePolicy = votePolicy;
    }

    @Transactional
    public PostResponse create(UUID authorId, CreatePostRequest request) {
        Instant now = Instant.now();
        validateClosesAt(request.closesAt(), now);
        Post post = postRepository.saveAndFlush(Post.create(
                authorId,
                request.title().trim(),
                request.content().trim(),
                normalizeCategory(request.category()),
                request.closesAt(),
                request.verdictThreshold() == null ? votePolicy.verdictThreshold() : request.verdictThreshold()
        ));
        eventPublisher.publishEvent(RankingChangedEvent.upsert(post.getId(), post.getVoteScore(), post.getCreatedAt()));
        return response(post, null);
    }

    @Transactional
    public PostResponse update(UUID authorId, UUID postId, UpdatePostRequest request) {
        Post post = findOwnedPost(authorId, postId);
        if (!post.isOpen()) {
            throw new ConflictException("Closed ballots cannot be edited");
        }
        Instant now = Instant.now();
        validateClosesAt(request.closesAt(), now);
        post.update(
                request.title().trim(),
                request.content().trim(),
                normalizeCategory(request.category()),
                request.closesAt(),
                request.verdictThreshold() == null ? post.getVerdictThreshold() : request.verdictThreshold()
        );
        postRepository.saveAndFlush(post);
        VoteType myVote = voteRepository.findByUserIdAndPostId(authorId, postId).map(Vote::getType).orElse(null);
        return response(post, myVote);
    }

    @Transactional
    public PostResponse close(UUID authorId, UUID postId) {
        Post post = findOwnedPost(authorId, postId);
        if (!post.isOpen()) {
            throw new ConflictException("Ballot is already closed");
        }
        post.close(Instant.now());
        postRepository.saveAndFlush(post);
        VoteType myVote = voteRepository.findByUserIdAndPostId(authorId, postId).map(Vote::getType).orElse(null);
        return response(post, myVote);
    }

    @Transactional
    public void delete(UUID authorId, UUID postId) {
        Post post = findOwnedPost(authorId, postId);
        voteRepository.deleteByPostId(postId);
        postRepository.delete(post);
        eventPublisher.publishEvent(RankingChangedEvent.delete(postId, post.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public PostResponse get(UUID postId, UUID userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        VoteType myVote = userId == null ? null : voteRepository.findByUserIdAndPostId(userId, postId)
                .map(Vote::getType).orElse(null);
        return response(post, myVote);
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> list(Pageable pageable, UUID userId, FeedType feed) {
        Page<Post> posts = rankingService.list(feed, pageable);
        if (userId == null || posts.isEmpty()) {
            return posts.map(post -> response(post, null));
        }
        Map<UUID, VoteType> votesByPostId = voteRepository.findByUserIdAndPostIdIn(
                        userId, posts.getContent().stream().map(Post::getId).toList())
                .stream().collect(Collectors.toMap(Vote::getPostId, Vote::getType, (first, ignored) -> first));
        return posts.map(post -> response(post, votesByPostId.get(post.getId())));
    }

    private PostResponse response(Post post, VoteType myVote) {
        return PostResponse.from(post, myVote);
    }

    private Post findOwnedPost(UUID authorId, UUID postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        if (!post.getAuthorId().equals(authorId)) {
            throw new ForbiddenException("Only the author can modify this post");
        }
        return post;
    }

    private String normalizeCategory(String category) {
        return category == null || category.isBlank()
                ? "GENERAL"
                : category.trim().toUpperCase(Locale.ROOT);
    }

    private void validateClosesAt(Instant closesAt, Instant now) {
        if (closesAt != null && !closesAt.isAfter(now)) {
            throw new ConflictException("closesAt must be in the future");
        }
    }
}
