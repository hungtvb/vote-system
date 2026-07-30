package com.hungtvb.votesystem.post;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID>, JpaSpecificationExecutor<Post> {
    Page<Post> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Optional<Post> findByIdAndModerationStatus(UUID postId, ModerationStatus moderationStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select post from Post post where post.id = :postId")
    Optional<Post> findByIdForUpdate(@Param("postId") UUID postId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select post
              from Post post
             where post.id = :postId
               and post.moderationStatus = com.hungtvb.votesystem.post.ModerationStatus.VISIBLE
            """)
    Optional<Post> findVisibleByIdForUpdate(@Param("postId") UUID postId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Post post
               set post.voteScore = post.voteScore + :scoreDelta,
                   post.upVotes = post.upVotes + :upDelta,
                   post.downVotes = post.downVotes + :downDelta,
                   post.updatedAt = :updatedAt
             where post.id = :postId
               and post.moderationStatus = com.hungtvb.votesystem.post.ModerationStatus.VISIBLE
               and post.upVotes + :upDelta >= 0
               and post.downVotes + :downDelta >= 0
            """)
    int incrementVoteTotals(@Param("postId") UUID postId,
                            @Param("scoreDelta") int scoreDelta,
                            @Param("upDelta") int upDelta,
                            @Param("downDelta") int downDelta,
                            @Param("updatedAt") Instant updatedAt);
}
