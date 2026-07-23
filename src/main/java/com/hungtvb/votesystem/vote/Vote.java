package com.hungtvb.votesystem.vote;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "votes", uniqueConstraints = @UniqueConstraint(
        name = "uk_votes_user_post", columnNames = {"user_id", "post_id"}))
public class Vote {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vote_type", nullable = false, length = 16)
    private VoteType type;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Vote() {
    }

    private Vote(UUID userId, UUID postId, VoteType type) {
        this.userId = userId;
        this.postId = postId;
        this.type = type;
    }

    public static Vote create(UUID userId, UUID postId, VoteType type) {
        return new Vote(userId, postId, type);
    }

    public void changeTo(VoteType type) {
        this.type = type;
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

    public VoteType getType() {
        return type;
    }
}
