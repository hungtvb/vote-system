package com.hungtvb.votesystem.vote;

import com.hungtvb.votesystem.common.error.ResourceNotFoundException;
import com.hungtvb.votesystem.post.PostRepository;
import com.hungtvb.votesystem.user.UserRepository;
import com.hungtvb.votesystem.vote.dto.VoteResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class VoteService {
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final VoteRepository voteRepository;

    public VoteService(UserRepository userRepository, PostRepository postRepository,
                       VoteRepository voteRepository) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.voteRepository = voteRepository;
    }

    @Transactional
    public VoteResponse cast(UUID userId, UUID postId, VoteType requestedType) {
        lockUser(userId);
        ensurePostExists(postId);

        Vote vote = voteRepository.findByUserIdAndPostId(userId, postId).orElse(null);
        int delta = 0;

        if (vote == null) {
            voteRepository.save(Vote.create(userId, postId, requestedType));
            delta = requestedType.delta();
        } else if (vote.getType() != requestedType) {
            delta = requestedType.delta() - vote.getType().delta();
            vote.changeTo(requestedType);
        }

        long score = delta == 0 ? currentScore(postId) : incrementScore(postId, delta);
        return new VoteResponse(postId, score, requestedType);
    }

    @Transactional
    public VoteResponse remove(UUID userId, UUID postId) {
        lockUser(userId);
        ensurePostExists(postId);

        Vote vote = voteRepository.findByUserIdAndPostId(userId, postId).orElse(null);
        if (vote == null) {
            return new VoteResponse(postId, currentScore(postId), null);
        }

        int delta = -vote.getType().delta();
        voteRepository.delete(vote);
        return new VoteResponse(postId, incrementScore(postId, delta), null);
    }

    private void lockUser(UUID userId) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private void ensurePostExists(UUID postId) {
        if (!postRepository.existsById(postId)) {
            throw new ResourceNotFoundException("Post not found");
        }
    }

    private long incrementScore(UUID postId, int delta) {
        if (postRepository.incrementVoteScore(postId, delta) == 0) {
            throw new ResourceNotFoundException("Post not found");
        }
        return currentScore(postId);
    }

    private long currentScore(UUID postId) {
        return postRepository.findVoteScoreById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
    }
}
