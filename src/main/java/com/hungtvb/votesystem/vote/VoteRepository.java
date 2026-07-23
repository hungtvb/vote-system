package com.hungtvb.votesystem.vote;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VoteRepository extends JpaRepository<Vote, UUID> {
    Optional<Vote> findByUserIdAndPostId(UUID userId, UUID postId);

    List<Vote> findByUserIdAndPostIdIn(UUID userId, Collection<UUID> postIds);
}
