package com.hungtvb.votesystem.comment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hungtvb.votesystem.comment.Comment;
import com.hungtvb.votesystem.comment.CommentModerationStatus;
import com.hungtvb.votesystem.post.dto.AuthorSummary;

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
        Instant editedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static CommentResponse from(Comment comment,
                                       AuthorSummary author,
                                       UUID currentUserId) {
        return new CommentResponse(
                comment.getId(),
                comment.getPostId(),
                comment.getParentId(),
                author,
                comment.publicBody(),
                comment.getModerationStatus(),
                currentUserId != null && currentUserId.equals(comment.getAuthorId()),
                comment.getEditedAt(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
