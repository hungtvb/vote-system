package com.hungtvb.votesystem.comment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hungtvb.votesystem.comment.Comment;
import com.hungtvb.votesystem.comment.CommentModerationStatus;
import com.hungtvb.votesystem.post.dto.AuthorSummary;
import com.hungtvb.votesystem.vote.VoteType;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommentResponse(
        UUID id,
        UUID postId,
        UUID parentId,
        AuthorSummary author,
        String body,
        CommentModerationStatus moderationStatus,
        boolean ownedByCurrentUser,
        long voteScore,
        long upVotes,
        long downVotes,
        VoteType myVote,
        Instant editedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static CommentResponse from(Comment comment,
                                       AuthorSummary author,
                                       UUID currentUserId,
                                       VoteType myVote) {
        return new CommentResponse(
                comment.getId(),
                comment.getPostId(),
                comment.getParentId(),
                author,
                comment.publicBody(),
                comment.getModerationStatus(),
                currentUserId != null && currentUserId.equals(comment.getAuthorId()),
                comment.getVoteScore(),
                comment.getUpVotes(),
                comment.getDownVotes(),
                myVote,
                comment.getEditedAt(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
