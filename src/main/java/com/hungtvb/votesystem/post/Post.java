package com.hungtvb.votesystem.post;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "posts")
public class Post {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

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

    protected Post() {
    }

    private Post(UUID authorId, String title, String content) {
        this.authorId = authorId;
        this.title = title;
        this.content = content;
        this.voteScore = 0;
        this.upVotes = 0;
        this.downVotes = 0;
    }

    public static Post create(UUID authorId, String title, String content) {
        return new Post(authorId, title, content);
    }

    public void update(String title, String content) {
        this.title = title;
        this.content = content;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getAuthorId() { return authorId; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public long getVoteScore() { return voteScore; }
    public long getUpVotes() { return upVotes; }
    public long getDownVotes() { return downVotes; }
    public long getTotalVotes() { return upVotes + downVotes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}