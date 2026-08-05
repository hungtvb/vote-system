package com.hungtvb.votesystem.admin.moderation;

import com.hungtvb.votesystem.admin.audit.AdminAuditAction;
import com.hungtvb.votesystem.admin.audit.AdminAuditEvent;
import com.hungtvb.votesystem.admin.audit.AdminAuditLogService;
import com.hungtvb.votesystem.admin.audit.AdminAuditTargetType;
import com.hungtvb.votesystem.admin.moderation.dto.AdminCommentModerationResponse;
import com.hungtvb.votesystem.comment.Comment;
import com.hungtvb.votesystem.comment.CommentModerationStatus;
import com.hungtvb.votesystem.comment.CommentRepository;
import com.hungtvb.votesystem.common.error.ConflictException;
import com.hungtvb.votesystem.common.error.ResourceNotFoundException;
import com.hungtvb.votesystem.post.Post;
import com.hungtvb.votesystem.post.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

@Service
public class AdminCommentModerationService {
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final AdminAuditLogService auditLogService;

    public AdminCommentModerationService(CommentRepository commentRepository,
                                         PostRepository postRepository,
                                         AdminAuditLogService auditLogService) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public AdminCommentModerationResponse hide(UUID actorId, UUID commentId, String reason) {
        return mutate(actorId, commentId, reason, AdminAuditAction.ADMIN_HIDE_COMMENT, Comment::hide);
    }

    @Transactional
    public AdminCommentModerationResponse restore(UUID actorId, UUID commentId, String reason) {
        return mutate(actorId, commentId, reason, AdminAuditAction.ADMIN_RESTORE_COMMENT, Comment::restore);
    }

    @Transactional
    public AdminCommentModerationResponse remove(UUID actorId, UUID commentId, String reason) {
        return mutate(actorId, commentId, reason, AdminAuditAction.ADMIN_DELETE_COMMENT, Comment::softDelete);
    }

    private AdminCommentModerationResponse mutate(UUID actorId,
                                                   UUID commentId,
                                                   String reason,
                                                   AdminAuditAction action,
                                                   BiConsumer<Comment, Instant> transition) {
        UUID postId = commentRepository.findPostIdById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
        Post post = postRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
        Comment comment = commentRepository.findByIdForUpdate(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
        if (!comment.getPostId().equals(postId)) {
            throw new ResourceNotFoundException("Comment not found");
        }

        CommentModerationStatus previousStatus = comment.getModerationStatus();
        boolean wasVisible = comment.isVisible();
        try {
            transition.accept(comment, Instant.now());
            boolean isVisible = comment.isVisible();
            if (wasVisible && !isVisible) {
                post.decrementCommentCount();
            } else if (!wasVisible && isVisible) {
                post.incrementCommentCount();
            }
        } catch (ArithmeticException | IllegalStateException exception) {
            throw new ConflictException("Comment moderation state does not allow this action");
        }

        auditLogService.append(new AdminAuditEvent(
                actorId,
                action,
                AdminAuditTargetType.COMMENT,
                commentId.toString(),
                reason,
                Map.of(
                        "post_id", postId.toString(),
                        "previous_status", previousStatus.name(),
                        "new_status", comment.getModerationStatus().name()
                )
        ));
        return AdminCommentModerationResponse.from(comment);
    }
}
