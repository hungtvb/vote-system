package com.hungtvb.votesystem.vote;

import com.hungtvb.votesystem.common.error.ConflictException;
import com.hungtvb.votesystem.common.error.ResourceNotFoundException;
import com.hungtvb.votesystem.post.Post;
import com.hungtvb.votesystem.post.PostRepository;
import com.hungtvb.votesystem.ranking.RankingChangedEvent;
import com.hungtvb.votesystem.user.UserRepository;
import com.hungtvb.votesystem.vote.dto.VoteResponse;
import com.hungtvb.votesystem.vote.metrics.VoteLatencyMetrics;
import com.hungtvb.votesystem.vote.stream.BallotVoteChangedEvent;
import com.hungtvb.votesystem.vote.stream.BallotVoteUpdate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static com.hungtvb.votesystem.vote.metrics.VoteLatencyMetrics.OPERATION_CAST;
import static com.hungtvb.votesystem.vote.metrics.VoteLatencyMetrics.OPERATION_REMOVE;

@Service
public class VoteService {
    private static final long DATABASE_TIMESTAMP_TICK_NANOS = 1_000L;

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final VoteRepository voteRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final VoteLatencyMetrics metrics;

    public VoteService(UserRepository userRepository,
                       PostRepository postRepository,
                       VoteRepository voteRepository,
                       ApplicationEventPublisher eventPublisher,
                       VotePolicy votePolicy,
                       VoteLatencyMetrics metrics) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.voteRepository = voteRepository;
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
    }

    @Transactional
    public VoteResponse cast(UUID userId, UUID postId, VoteType requestedType) {
        return metrics.timeStage(OPERATION_CAST, "transaction_work", () -> {
            lockUser(OPERATION_CAST, userId);
            Post post = findPostForUpdate(OPERATION_CAST, postId);
            requireOpen(post);

            Vote vote = metrics.timeStage(OPERATION_CAST, "vote_lookup",
                    () -> voteRepository.findByUserIdAndPostId(userId, postId).orElse(null));
            VoteDelta delta = VoteDelta.none();

            if (vote == null) {
                metrics.timeStage(OPERATION_CAST, "vote_mutation",
                        () -> voteRepository.save(Vote.create(userId, postId, requestedType)));
                delta = VoteDelta.add(requestedType);
            } else if (vote.getType() != requestedType) {
                delta = VoteDelta.change(vote.getType(), requestedType);
                Vote existingVote = vote;
                metrics.timeStage(OPERATION_CAST, "vote_mutation",
                        () -> existingVote.changeTo(requestedType));
            }

            Post updatedPost = delta.isEmpty() ? post : incrementTotals(OPERATION_CAST, post, delta);
            publishEvents(OPERATION_CAST, postId, updatedPost, !delta.isEmpty());
            return VoteResponse.from(updatedPost, requestedType, updatedPost.getVerdictThreshold());
        });
    }

    @Transactional
    public VoteResponse remove(UUID userId, UUID postId) {
        return metrics.timeStage(OPERATION_REMOVE, "transaction_work", () -> {
            lockUser(OPERATION_REMOVE, userId);
            Post post = findPostForUpdate(OPERATION_REMOVE, postId);
            requireOpen(post);

            Vote vote = metrics.timeStage(OPERATION_REMOVE, "vote_lookup",
                    () -> voteRepository.findByUserIdAndPostId(userId, postId).orElse(null));
            if (vote == null) {
                publishEvents(OPERATION_REMOVE, postId, post, false);
                return VoteResponse.from(post, null, post.getVerdictThreshold());
            }

            VoteDelta delta = VoteDelta.remove(vote.getType());
            metrics.timeStage(OPERATION_REMOVE, "vote_mutation", () -> voteRepository.delete(vote));
            Post updatedPost = incrementTotals(OPERATION_REMOVE, post, delta);
            publishEvents(OPERATION_REMOVE, postId, updatedPost, true);
            return VoteResponse.from(updatedPost, null, updatedPost.getVerdictThreshold());
        });
    }

    private void lockUser(String operation, UUID userId) {
        metrics.timeStage(operation, "user_lock", () -> userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found")));
    }

    private Post findPostForUpdate(String operation, UUID postId) {
        return metrics.timeStage(operation, "post_lock", () -> postRepository.findVisibleByIdForUpdate(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found")));
    }

    private Post findPost(String operation, UUID postId) {
        return metrics.timeStage(operation, "post_reload", () -> postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found")));
    }

    private void requireOpen(Post post) {
        if (!post.acceptsVotes(Instant.now())) {
            throw new ConflictException("Ballot is closed and no longer accepts votes");
        }
    }

    private Post incrementTotals(String operation, Post post, VoteDelta delta) {
        Instant updatedAt = Instant.now();
        if (!updatedAt.isAfter(post.getUpdatedAt())) {
            updatedAt = post.getUpdatedAt().plusNanos(DATABASE_TIMESTAMP_TICK_NANOS);
        }
        Instant aggregateUpdatedAt = updatedAt;
        int updatedRows = metrics.timeStage(operation, "aggregate_write", () -> postRepository.incrementVoteTotals(
                post.getId(), delta.scoreDelta(), delta.upDelta(), delta.downDelta(), aggregateUpdatedAt));
        if (updatedRows == 0) {
            throw new IllegalStateException("Vote aggregate update would produce invalid counts");
        }
        return findPost(operation, post.getId());
    }

    private void publishEvents(String operation, UUID postId, Post post, boolean voteChanged) {
        metrics.timeStage(operation, "event_publish", () -> {
            eventPublisher.publishEvent(RankingChangedEvent.upsert(
                    postId, post.getVoteScore(), post.getCreatedAt()));
            if (voteChanged) {
                eventPublisher.publishEvent(new BallotVoteChangedEvent(BallotVoteUpdate.from(post)));
            }
        });
    }

    private record VoteDelta(int scoreDelta, int upDelta, int downDelta) {
        static VoteDelta none() { return new VoteDelta(0, 0, 0); }
        static VoteDelta add(VoteType type) {
            return type == VoteType.UP ? new VoteDelta(1, 1, 0) : new VoteDelta(-1, 0, 1);
        }
        static VoteDelta remove(VoteType type) {
            return type == VoteType.UP ? new VoteDelta(-1, -1, 0) : new VoteDelta(1, 0, -1);
        }
        static VoteDelta change(VoteType previous, VoteType requested) {
            VoteDelta remove = remove(previous);
            VoteDelta add = add(requested);
            return new VoteDelta(remove.scoreDelta + add.scoreDelta,
                    remove.upDelta + add.upDelta,
                    remove.downDelta + add.downDelta);
        }
        boolean isEmpty() { return scoreDelta == 0 && upDelta == 0 && downDelta == 0; }
    }
}
