package com.hungtvb.votesystem.post;

import com.hungtvb.votesystem.vote.VoteVerdict;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "posts")
public class Post {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "ballot_number", nullable = false, unique = true, length = 32)
    private String ballotNumber;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, length = 50)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BallotStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false, length = 16)
    private ModerationStatus moderationStatus;

    @Column(name = "moderation_updated_at")
    private Instant moderationUpdatedAt;

    @Column(name = "closes_at")
    private Instant closesAt;

    @Column(name = "verdict_threshold", nullable = false)
    private int verdictThreshold;

    @Enumerated(EnumType.STRING)
    @Column(name = "final_verdict", length = 16)
    private VoteVerdict finalVerdict;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "vote_score", nullable = false)
    private long voteScore;

    @Column(name = "up_votes", nullable = false)
    private long upVotes;

    @Column(name = "down_votes", nullable = false)
    private long downVotes;

    @Column(name = "comment_count", nullable = false)
    private long commentCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Post() {
    }

    private Post(UUID authorId, String title, String content, String category,
                 Instant closesAt, int verdictThreshold) {
        this.authorId = authorId;
        this.ballotNumber = "BAL-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 8).toUpperCase(Locale.ROOT);
        this.title = title;
        this.content = content;
        this.category = category;
        this.status = BallotStatus.OPEN;
        this.moderationStatus = ModerationStatus.VISIBLE;
        this.closesAt = closesAt;
        this.verdictThreshold = verdictThreshold;
        this.voteScore = 0;
        this.upVotes = 0;
        this.downVotes = 0;
        this.commentCount = 0;
    }

    public static Post create(UUID authorId, String title, String content, String category,
                              Instant closesAt, int verdictThreshold) {
        return new Post(authorId, title, content, category, closesAt, verdictThreshold);
    }

    public void update(String title, String content, String category, Instant closesAt, int verdictThreshold) {
        if (!isOpen()) {
            throw new IllegalStateException("Closed ballots cannot be edited");
        }
        this.title = title;
        this.content = content;
        this.category = category;
        this.closesAt = closesAt;
        this.verdictThreshold = verdictThreshold;
    }

    public void close(Instant now) {
        if (!isOpen()) {
            return;
        }
        this.status = BallotStatus.CLOSED;
        this.closedAt = now;
        this.finalVerdict = VoteVerdict.from(upVotes, downVotes, verdictThreshold);
    }

    public void hide(Instant now) {
        if (moderationStatus != ModerationStatus.VISIBLE) {
            throw new IllegalStateException("Only visible ballots can be hidden");
        }
        moderationStatus = ModerationStatus.HIDDEN;
        moderationUpdatedAt = now;
    }

    public void restore(Instant now) {
        if (moderationStatus != ModerationStatus.HIDDEN) {
            throw new IllegalStateException("Only hidden ballots can be restored");
        }
        moderationStatus = ModerationStatus.VISIBLE;
        moderationUpdatedAt = now;
    }

    public void softDelete(Instant now) {
        if (moderationStatus == ModerationStatus.DELETED) {
            throw new IllegalStateException("Deleted ballots cannot be deleted again");
        }
        moderationStatus = ModerationStatus.DELETED;
        moderationUpdatedAt = now;
    }

    public boolean isOpen() {
        return status == BallotStatus.OPEN;
    }

    public boolean isPubliclyVisible() {
        return moderationStatus == ModerationStatus.VISIBLE;
    }

    public void incrementCommentCount() {
        commentCount = Math.incrementExact(commentCount);
    }

    public void decrementCommentCount() {
        if (commentCount <= 0) {
            throw new IllegalStateException("Comment count cannot become negative");
        }
        commentCount--;
    }

    public boolean acceptsVotes(Instant now) {
        return isPubliclyVisible() && isOpen() && (closesAt == null || now.isBefore(closesAt));
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (moderationStatus == null) {
            moderationStatus = ModerationStatus.VISIBLE;
        }
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getAuthorId() { return authorId; }
    public String getBallotNumber() { return ballotNumber; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getCategory() { return category; }
    public BallotStatus getStatus() { return status; }
    public ModerationStatus getModerationStatus() { return moderationStatus; }
    public Instant getModerationUpdatedAt() { return moderationUpdatedAt; }
    public Instant getClosesAt() { return closesAt; }
    public int getVerdictThreshold() { return verdictThreshold; }
    public VoteVerdict getFinalVerdict() { return finalVerdict; }
    public Instant getClosedAt() { return closedAt; }
    public long getVoteScore() { return voteScore; }
    public long getUpVotes() { return upVotes; }
    public long getDownVotes() { return downVotes; }
    public long getTotalVotes() { return upVotes + downVotes; }
    public long getCommentCount() { return commentCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
