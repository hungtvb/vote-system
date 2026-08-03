package com.hungtvb.votesystem.comment;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {
    @Query("select comment.postId from Comment comment where comment.id = :commentId")
    Optional<UUID> findPostIdById(@Param("commentId") UUID commentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select comment from Comment comment where comment.id = :commentId")
    Optional<Comment> findByIdForUpdate(@Param("commentId") UUID commentId);

    @Query(value = """
            select comment.*
              from comments comment
             where comment.post_id = :postId
               and (
                    cast(:afterCreatedAt as timestamptz) is null
                    or comment.created_at > :afterCreatedAt
                    or (comment.created_at = :afterCreatedAt and comment.id > :afterId)
               )
             order by comment.created_at asc, comment.id asc
            """, nativeQuery = true)
    List<Comment> findPage(@Param("postId") UUID postId,
                           @Param("afterCreatedAt") Instant afterCreatedAt,
                           @Param("afterId") UUID afterId,
                           Pageable pageable);

    @Query(value = """
            select comment.*
              from comments comment
              join posts post on post.id = comment.post_id
             where comment.id = :commentId
               and comment.moderation_status = 'VISIBLE'
               and post.moderation_status = 'VISIBLE'
            """, nativeQuery = true)
    Optional<Comment> findReportableById(@Param("commentId") UUID commentId);
}
