package com.hungtvb.votesystem.comment;

import com.hungtvb.votesystem.comment.dto.CommentPageResponse;
import com.hungtvb.votesystem.comment.dto.CommentResponse;
import com.hungtvb.votesystem.comment.dto.CreateCommentRequest;
import com.hungtvb.votesystem.comment.dto.UpdateCommentRequest;
import com.hungtvb.votesystem.common.error.ConflictException;
import com.hungtvb.votesystem.common.error.ForbiddenException;
import com.hungtvb.votesystem.common.error.InvalidRequestException;
import com.hungtvb.votesystem.common.error.ResourceNotFoundException;
import com.hungtvb.votesystem.post.ModerationStatus;
import com.hungtvb.votesystem.post.Post;
import com.hungtvb.votesystem.post.PostRepository;
import com.hungtvb.votesystem.post.dto.AuthorSummary;
import com.hungtvb.votesystem.user.UserRepository;
import com.hungtvb.votesystem.vote.VoteType;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CommentService {
    private static final int MAX_PAGE_LIMIT = 100;

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CommentVoteRepository voteRepository;

    public CommentService(CommentRepository commentRepository,
                          PostRepository postRepository,
                          UserRepository userRepository,
                          CommentVoteRepository voteRepository) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.voteRepository = voteRepository;
    }

    @Transactional(readOnly = true)
    public CommentPageResponse list(UUID postId,
                                    UUID currentUserId,
                                    Instant afterCreatedAt,
                                    UUID afterId,
                                    int requestedLimit) {
        requirePublicPost(postId);
        int limit = validateCursor(afterCreatedAt, afterId, requestedLimit);
        List<Comment> fetched = new ArrayList<>(commentRepository.findPage(
                postId,
                afterCreatedAt,
                afterId,
                PageRequest.of(0, limit + 1)
        ));
        boolean hasMore = fetched.size() > limit;
        if (hasMore) {
            fetched.remove(fetched.size() - 1);
        }
        Map<UUID, AuthorSummary> authors = userRepository.findAllById(
                        fetched.stream().map(Comment::getAuthorId).distinct().toList())
                .stream()
                .map(AuthorSummary::from)
                .collect(Collectors.toMap(AuthorSummary::id, author -> author));
        Map<UUID, VoteType> currentVotes = currentUserId == null || fetched.isEmpty()
                ? Map.of()
                : voteRepository.findByUserIdAndCommentIdIn(
                                currentUserId,
                                fetched.stream().map(Comment::getId).toList())
                        .stream()
                        .collect(Collectors.toMap(CommentVote::getCommentId, CommentVote::getType));
        List<CommentResponse> content = fetched.stream()
                .map(comment -> CommentResponse.from(
                        comment,
                        authors.getOrDefault(comment.getAuthorId(), AuthorSummary.technical(comment.getAuthorId())),
                        currentUserId,
                        currentVotes.get(comment.getId())
                ))
                .toList();
        Comment last = fetched.isEmpty() ? null : fetched.get(fetched.size() - 1);
        return new CommentPageResponse(
                content,
                hasMore ? last.getCreatedAt() : null,
                hasMore ? last.getId() : null,
                hasMore
        );
    }

    @Transactional
    public CommentResponse create(UUID authorId, UUID postId, CreateCommentRequest request) {
        Post post = lockPublicPost(postId);
        UUID parentId = request.parentId();
        if (parentId != null) {
            Comment parent = commentRepository.findByIdForUpdate(parentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Parent comment not found"));
            if (!parent.getPostId().equals(postId)) {
                throw new InvalidRequestException("Parent comment belongs to another ballot");
            }
            if (!parent.acceptsReply()) {
                throw new ConflictException("Replies are limited to one visible parent level");
            }
        }
        Instant now = Instant.now();
        Comment comment = commentRepository.saveAndFlush(Comment.create(
                postId,
                authorId,
                parentId,
                normalizeBody(request.body()),
                now
        ));
        post.incrementCommentCount();
        return response(comment, authorId);
    }

    @Transactional
    public CommentResponse edit(UUID authorId, UUID commentId, UpdateCommentRequest request) {
        Comment comment = lockOwnedVisibleComment(authorId, commentId).comment();
        try {
            comment.edit(normalizeBody(request.body()), Instant.now());
        } catch (IllegalStateException exception) {
            throw new ConflictException(exception.getMessage());
        }
        return response(comment, authorId);
    }

    @Transactional
    public void remove(UUID authorId, UUID commentId) {
        LockedComment locked = lockOwnedComment(authorId, commentId);
        try {
            if (locked.comment().removeByAuthor(Instant.now())) {
                locked.post().decrementCommentCount();
            }
        } catch (IllegalStateException exception) {
            throw new ConflictException(exception.getMessage());
        }
    }

    private LockedComment lockOwnedVisibleComment(UUID authorId, UUID commentId) {
        LockedComment locked = lockOwnedComment(authorId, commentId);
        if (!locked.comment().isVisible()) {
            throw new ConflictException("Only visible comments can be edited");
        }
        return locked;
    }

    private LockedComment lockOwnedComment(UUID authorId, UUID commentId) {
        UUID postId = commentRepository.findPostIdById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
        Post post = lockPublicPost(postId);
        Comment comment = commentRepository.findByIdForUpdate(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
        if (!comment.getAuthorId().equals(authorId)) {
            throw new ForbiddenException("Only the author can modify this comment");
        }
        return new LockedComment(post, comment);
    }

    private CommentResponse response(Comment comment, UUID currentUserId) {
        AuthorSummary author = userRepository.findById(comment.getAuthorId())
                .map(AuthorSummary::from)
                .orElseGet(() -> AuthorSummary.technical(comment.getAuthorId()));
        VoteType myVote = currentUserId == null
                ? null
                : voteRepository.findByUserIdAndCommentId(currentUserId, comment.getId())
                        .map(CommentVote::getType)
                        .orElse(null);
        return CommentResponse.from(comment, author, currentUserId, myVote);
    }

    private Post lockPublicPost(UUID postId) {
        return postRepository.findVisibleByIdForUpdate(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
    }

    private void requirePublicPost(UUID postId) {
        postRepository.findByIdAndModerationStatus(postId, ModerationStatus.VISIBLE)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
    }

    private int validateCursor(Instant afterCreatedAt, UUID afterId, int requestedLimit) {
        if ((afterCreatedAt == null) != (afterId == null)) {
            throw new InvalidRequestException("Both comment cursor fields are required together");
        }
        if (requestedLimit < 1 || requestedLimit > MAX_PAGE_LIMIT) {
            throw new InvalidRequestException("Comment limit must be between 1 and " + MAX_PAGE_LIMIT);
        }
        return requestedLimit;
    }

    private record LockedComment(Post post, Comment comment) {
    }

    private String normalizeBody(String body) {
        String normalized = body == null ? "" : body.strip();
        if (normalized.isBlank() || normalized.length() > 2000) {
            throw new InvalidRequestException("Comment body is invalid");
        }
        return normalized;
    }
}
