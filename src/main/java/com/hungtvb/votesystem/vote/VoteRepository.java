package com.hungtvb.votesystem.vote;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VoteRepository extends JpaRepository<Vote, UUID> {
    Optional<Vote> findByUserIdAndPostId(UUID userId, UUID postId);

    List<Vote> findByUserIdAndPostIdIn(UUID userId, Collection<UUID> postIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Vote vote where vote.postId = :postId")
    int deleteByPostId(@Param("postId") UUID postId);
}