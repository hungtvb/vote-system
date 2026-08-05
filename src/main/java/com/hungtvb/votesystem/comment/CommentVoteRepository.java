package com.hungtvb.votesystem.comment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommentVoteRepository extends JpaRepository<CommentVote, UUID> {
    Optional<CommentVote> findByUserIdAndCommentId(UUID userId, UUID commentId);

    List<CommentVote> findByUserIdAndCommentIdIn(UUID userId, Collection<UUID> commentIds);
}
