package com.hungtvb.votesystem.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {
    Page<Post> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Post post set post.voteScore = post.voteScore + :delta where post.id = :postId")
    int incrementVoteScore(@Param("postId") UUID postId, @Param("delta") int delta);

    @Query("select post.voteScore from Post post where post.id = :postId")
    Optional<Long> findVoteScoreById(@Param("postId") UUID postId);
}
