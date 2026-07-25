package com.hungtvb.votesystem.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID>, JpaSpecificationExecutor<Post> {
    Page<Post> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Post post
               set post.voteScore = post.voteScore + :scoreDelta,
                   post.upVotes = post.upVotes + :upDelta,
                   post.downVotes = post.downVotes + :downDelta,
                   post.updatedAt = :updatedAt
             where post.id = :postId
               and post.upVotes + :upDelta >= 0
               and post.downVotes + :downDelta >= 0
            """)
    int incrementVoteTotals(@Param("postId") UUID postId,
                            @Param("scoreDelta") int scoreDelta,
                            @Param("upDelta") int upDelta,
                            @Param("downDelta") int downDelta,
                            @Param("updatedAt") Instant updatedAt);
}
