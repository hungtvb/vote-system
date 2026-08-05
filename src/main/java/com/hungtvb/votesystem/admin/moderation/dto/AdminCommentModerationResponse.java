package com.hungtvb.votesystem.admin.moderation.dto;

import com.hungtvb.votesystem.comment.Comment;
import com.hungtvb.votesystem.comment.CommentModerationStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminCommentModerationResponse(
        UUID id,
        UUID postId,
        CommentModerationStatus moderationStatus,
        Instant moderationUpdatedAt,
        long voteScore,
        long upVotes,
        long downVotes
) {
    public static AdminCommentModerationResponse from(Comment comment) {
        return new AdminCommentModerationResponse(
                comment.getId(),
                comment.getPostId(),
                comment.getModerationStatus(),
                comment.getModerationUpdatedAt(),
                comment.getVoteScore(),
                comment.getUpVotes(),
                comment.getDownVotes()
        );
    }
}
