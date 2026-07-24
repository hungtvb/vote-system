package com.hungtvb.votesystem.vote;

import com.hungtvb.votesystem.common.error.ResourceNotFoundException;
import com.hungtvb.votesystem.post.Post;
import com.hungtvb.votesystem.post.PostRepository;
import com.hungtvb.votesystem.ranking.RankingChangedEvent;
import com.hungtvb.votesystem.user.UserRepository;
import com.hungtvb.votesystem.vote.dto.VoteResponse;
import com.hungtvb.votesystem.vote.dto.VoteSummary;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class VoteService {
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final VoteRepository voteRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final VotePolicy votePolicy;

    public VoteService(UserRepository userRepository,
                       PostRepository postRepository,
                       VoteRepository voteRepository,
                       ApplicationEventPublisher eventPublisher,
                       VotePolicy votePolicy) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.voteRepository = voteRepository;
        this.eventPublisher = eventPublisher;
        this.votePolicy = votePolicy;
    }

    @Transactional
    public VoteResponse cast(UUID userId, UUID postId, VoteType requestedType) {
        lockUser(userId);
        Post post = findPost(postId);

        Vote vote = voteRepository.findByUserIdAndPostId(userId, postId).orElse(null);
        VoteDelta delta = VoteDelta.none();

        if (vote == null) {
            voteRepository.save(Vote.create(userId, postId, requestedType));
            delta = VoteDelta.add(requestedType);
        } else if (vote.getType() != requestedType) {
            delta = VoteDelta.change(vote.getType(), requestedType);
            vote.changeTo(requestedType);
        }

        Post updatedPost = delta.isEmpty() ? post : incrementTotals(postId, delta);
        eventPublisher.publishEvent(RankingChangedEvent.upsert(
                postId, updatedPost.getVoteScore(), updatedPost.getCreatedAt()));
        return response(updatedPost, requestedType);
    }

    @Transactional
    public VoteResponse remove(UUID userId, UUID postId) {
        lockUser(userId);
        Post post = findPost(postId);

        Vote vote = voteRepository.findByUserIdAndPostId(userId, postId).orElse(null);
        if (vote == null) {
            eventPublisher.publishEvent(RankingChangedEvent.upsert(postId, post.getVoteScore(), post.getCreatedAt()));
            return response(post, null);
        }

        VoteDelta delta = VoteDelta.remove(vote.getType());
        voteRepository.delete(vote);
        Post updatedPost = incrementTotals(postId, delta);
        eventPublisher.publishEvent(RankingChangedEvent.upsert(
                postId, updatedPost.getVoteScore(), updatedPost.getCreatedAt()));
        return response(updatedPost, null);
    }

    private void lockUser(UUID userId) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Post findPost(UUID postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
    }

    private Post incrementTotals(UUID postId, VoteDelta delta) {
        if (postRepository.incrementVoteTotals(
                postId, delta.scoreDelta(), delta.upDelta(), delta.downDelta()) == 0) {
            throw new IllegalStateException("Vote aggregate update would produce invalid counts");
        }
        return findPost(postId);
    }

    private VoteResponse response(Post post, VoteType myVote) {
        return new VoteResponse(
                post.getId(),
                VoteSummary.from(post, myVote, votePolicy.verdictThreshold())
        );
    }

    private record VoteDelta(int scoreDelta, int upDelta, int downDelta) {
        static VoteDelta none() {
            return new VoteDelta(0, 0, 0);
        }

        static VoteDelta add(VoteType type) {
            return type == VoteType.UP
                    ? new VoteDelta(1, 1, 0)
                    : new VoteDelta(-1, 0, 1);
        }

        static VoteDelta remove(VoteType type) {
            return type == VoteType.UP
                    ? new VoteDelta(-1, -1, 0)
                    : new VoteDelta(1, 0, -1);
        }

        static VoteDelta change(VoteType previous, VoteType requested) {
            VoteDelta remove = remove(previous);
            VoteDelta add = add(requested);
            return new VoteDelta(
                    remove.scoreDelta + add.scoreDelta,
                    remove.upDelta + add.upDelta,
                    remove.downDelta + add.downDelta
            );
        }

        boolean isEmpty() {
            return scoreDelta == 0 && upDelta == 0 && downDelta == 0;
        }
    }
}