package com.hungtvb.votesystem.comment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "comments")
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "post_id", nullable = false, updatable = false)
    private UUID postId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Column(name = "parent_id", updatable = false)
    private UUID parentId;

    @Column(nullable = false, length = 2000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false, length = 32)
    private CommentModerationStatus moderationStatus;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "moderation_updated_at")
    private Instant moderationUpdatedAt;

    @Column(name = "vote_score", nullable = false)
    private long voteScore;

    @Column(name = "up_votes", nullable = false)
    private long upVotes;

    @Column(name = "down_votes", nullable = false)
    private long downVotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Comment() {
    }

    private Comment(UUID postId, UUID authorId, UUID parentId, String body, Instant now) {
        this.postId = Objects.requireNonNull(postId);
        this.authorId = Objects.requireNonNull(authorId);
        this.parentId = parentId;
        this.body = Objects.requireNonNull(body);
        this.moderationStatus = CommentModerationStatus.VISIBLE;
        this.voteScore = 0;
        this.upVotes = 0;
        this.downVotes = 0;
        this.createdAt = Objects.requireNonNull(now);
        this.updatedAt = now;
    }

    public static Comment create(UUID postId, UUID authorId, UUID parentId, String body, Instant now) {
        return new Comment(postId, authorId, parentId, body, now);
    }

    public boolean edit(String body, Instant now) {
        requireVisible("Only visible comments can be edited");
        if (this.body.equals(body)) {
            return false;
        }
        this.body = Objects.requireNonNull(body);
        this.editedAt = Objects.requireNonNull(now);
        this.updatedAt = now;
        return true;
    }

    public boolean removeByAuthor(Instant now) {
        if (moderationStatus == CommentModerationStatus.REMOVED_BY_AUTHOR) {
            return false;
        }
        requireVisible("Only visible comments can be removed by their author");
        moderationStatus = CommentModerationStatus.REMOVED_BY_AUTHOR;
        removedAt = Objects.requireNonNull(now);
        moderationUpdatedAt = now;
        updatedAt = now;
        return true;
    }


    public void hide(Instant now) {
        if (moderationStatus != CommentModerationStatus.VISIBLE) {
            throw new IllegalStateException("Only visible comments can be hidden");
        }
        moderationStatus = CommentModerationStatus.HIDDEN;
        removedAt = Objects.requireNonNull(now);
        moderationUpdatedAt = now;
        updatedAt = now;
    }

    public void restore(Instant now) {
        if (moderationStatus != CommentModerationStatus.HIDDEN) {
            throw new IllegalStateException("Only hidden comments can be restored");
        }
        moderationStatus = CommentModerationStatus.VISIBLE;
        removedAt = null;
        moderationUpdatedAt = Objects.requireNonNull(now);
        updatedAt = now;
    }

    public void softDelete(Instant now) {
        if (moderationStatus == CommentModerationStatus.DELETED) {
            throw new IllegalStateException("Deleted comments cannot be deleted again");
        }
        moderationStatus = CommentModerationStatus.DELETED;
        removedAt = Objects.requireNonNull(now);
        moderationUpdatedAt = now;
        updatedAt = now;
    }

    public void applyVoteDelta(int scoreDelta, int upDelta, int downDelta, Instant now) {
        long nextScore = Math.addExact(voteScore, scoreDelta);
        long nextUpVotes = Math.addExact(upVotes, upDelta);
        long nextDownVotes = Math.addExact(downVotes, downDelta);
        if (nextUpVotes < 0 || nextDownVotes < 0 || nextScore != nextUpVotes - nextDownVotes) {
            throw new IllegalStateException("Comment vote aggregates are invalid");
        }
        voteScore = nextScore;
        upVotes = nextUpVotes;
        downVotes = nextDownVotes;
        updatedAt = Objects.requireNonNull(now);
    }

    public boolean acceptsReply() {
        return parentId == null && moderationStatus == CommentModerationStatus.VISIBLE;
    }

    public boolean isVisible() {
        return moderationStatus == CommentModerationStatus.VISIBLE;
    }

    public String publicBody() {
        return isVisible() ? body : null;
    }

    private void requireVisible(String message) {
        if (!isVisible()) {
            throw new IllegalStateException(message);
        }
    }

    public UUID getId() { return id; }
    public UUID getPostId() { return postId; }
    public UUID getAuthorId() { return authorId; }
    public UUID getParentId() { return parentId; }
    public String getBody() { return body; }
    public CommentModerationStatus getModerationStatus() { return moderationStatus; }
    public Instant getEditedAt() { return editedAt; }
    public Instant getRemovedAt() { return removedAt; }
    public Instant getModerationUpdatedAt() { return moderationUpdatedAt; }
    public long getVoteScore() { return voteScore; }
    public long getUpVotes() { return upVotes; }
    public long getDownVotes() { return downVotes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
