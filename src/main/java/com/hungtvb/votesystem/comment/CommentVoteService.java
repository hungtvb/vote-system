package com.hungtvb.votesystem.comment;

import com.hungtvb.votesystem.comment.dto.CommentVoteResponse;
import com.hungtvb.votesystem.common.error.ConflictException;
import com.hungtvb.votesystem.common.error.ResourceNotFoundException;
import com.hungtvb.votesystem.post.PostRepository;
import com.hungtvb.votesystem.vote.VoteType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class CommentVoteService {
    private final CommentRepository commentRepository;
    private final CommentVoteRepository voteRepository;
    private final PostRepository postRepository;

    public CommentVoteService(CommentRepository commentRepository,
                              CommentVoteRepository voteRepository,
                              PostRepository postRepository) {
        this.commentRepository = commentRepository;
        this.voteRepository = voteRepository;
        this.postRepository = postRepository;
    }

    @Transactional
    public CommentVoteResponse cast(UUID userId, UUID commentId, VoteType requestedType) {
        Comment comment = lockVisibleComment(commentId);
        CommentVote vote = voteRepository.findByUserIdAndCommentId(userId, commentId).orElse(null);
        VoteDelta delta = VoteDelta.none();
        Instant now = Instant.now();

        if (vote == null) {
            voteRepository.saveAndFlush(CommentVote.create(userId, commentId, requestedType, now));
            delta = VoteDelta.add(requestedType);
        } else {
            VoteType previousType = vote.getType();
            if (vote.changeTo(requestedType, now)) {
                delta = VoteDelta.change(previousType, requestedType);
            }
        }

        if (!delta.isEmpty()) {
            apply(comment, delta, now);
        }
        return CommentVoteResponse.from(comment, requestedType);
    }

    @Transactional
    public CommentVoteResponse remove(UUID userId, UUID commentId) {
        Comment comment = lockVisibleComment(commentId);
        CommentVote vote = voteRepository.findByUserIdAndCommentId(userId, commentId).orElse(null);
        if (vote == null) {
            return CommentVoteResponse.from(comment, null);
        }

        VoteDelta delta = VoteDelta.remove(vote.getType());
        voteRepository.delete(vote);
        apply(comment, delta, Instant.now());
        return CommentVoteResponse.from(comment, null);
    }

    private Comment lockVisibleComment(UUID commentId) {
        UUID postId = commentRepository.findPostIdById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
        postRepository.findVisibleByIdForRead(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
        Comment comment = commentRepository.findByIdForUpdate(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
        if (!comment.getPostId().equals(postId) || !comment.isVisible()) {
            throw new ResourceNotFoundException("Comment not found");
        }
        return comment;
    }

    private void apply(Comment comment, VoteDelta delta, Instant now) {
        try {
            comment.applyVoteDelta(delta.scoreDelta(), delta.upDelta(), delta.downDelta(), now);
        } catch (ArithmeticException | IllegalStateException exception) {
            throw new ConflictException("Comment vote aggregate update is invalid");
        }
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
            return new VoteDelta(
                    remove.scoreDelta + add.scoreDelta,
                    remove.upDelta + add.upDelta,
                    remove.downDelta + add.downDelta
            );
        }
        boolean isEmpty() { return scoreDelta == 0 && upDelta == 0 && downDelta == 0; }
    }
}
