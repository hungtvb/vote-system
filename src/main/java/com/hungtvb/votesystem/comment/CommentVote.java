package com.hungtvb.votesystem.comment;

import com.hungtvb.votesystem.vote.VoteType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "comment_votes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_comment_votes_user_comment",
                columnNames = {"user_id", "comment_id"}),
        indexes = @Index(
                name = "idx_comment_votes_comment_type",
                columnList = "comment_id, vote_type, user_id")
)
public class CommentVote {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "comment_id", nullable = false, updatable = false)
    private UUID commentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vote_type", nullable = false, length = 16)
    private VoteType type;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CommentVote() {
    }

    private CommentVote(UUID userId, UUID commentId, VoteType type, Instant now) {
        this.userId = Objects.requireNonNull(userId);
        this.commentId = Objects.requireNonNull(commentId);
        this.type = Objects.requireNonNull(type);
        this.createdAt = Objects.requireNonNull(now);
        this.updatedAt = now;
    }

    public static CommentVote create(UUID userId, UUID commentId, VoteType type, Instant now) {
        return new CommentVote(userId, commentId, type, now);
    }

    public boolean changeTo(VoteType requestedType, Instant now) {
        if (type == requestedType) {
            return false;
        }
        type = Objects.requireNonNull(requestedType);
        updatedAt = Objects.requireNonNull(now);
        return true;
    }

    public UUID getCommentId() { return commentId; }
    public VoteType getType() { return type; }
}
