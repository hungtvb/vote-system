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
        updatedAt = now;
        return true;
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
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
