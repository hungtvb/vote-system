package com.hungtvb.votesystem.moderation;

import com.hungtvb.votesystem.admin.moderation.AdminCommentModerationService;
import com.hungtvb.votesystem.admin.moderation.AdminPostModerationService;
import com.hungtvb.votesystem.admin.usermoderation.AdminUserModerationService;
import com.hungtvb.votesystem.comment.Comment;
import com.hungtvb.votesystem.comment.CommentRepository;
import com.hungtvb.votesystem.common.error.InvalidRequestException;
import com.hungtvb.votesystem.common.error.ResourceNotFoundException;
import com.hungtvb.votesystem.post.ModerationStatus;
import com.hungtvb.votesystem.post.PostRepository;
import com.hungtvb.votesystem.user.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class ModerationTargetService {
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final AdminPostModerationService postModerationService;
    private final AdminCommentModerationService commentModerationService;
    private final AdminUserModerationService userModerationService;

    public ModerationTargetService(PostRepository postRepository,
                                   UserRepository userRepository,
                                   CommentRepository commentRepository,
                                   AdminPostModerationService postModerationService,
                                   AdminCommentModerationService commentModerationService,
                                   AdminUserModerationService userModerationService) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.commentRepository = commentRepository;
        this.postModerationService = postModerationService;
        this.commentModerationService = commentModerationService;
        this.userModerationService = userModerationService;
    }

    public TargetValidationStatus validateForReport(ModerationTargetType targetType,
                                                     UUID targetId,
                                                     UUID reporterId) {
        return switch (targetType) {
            case BALLOT -> {
                postRepository.findByIdAndModerationStatus(targetId, ModerationStatus.VISIBLE)
                        .orElseThrow(() -> new ResourceNotFoundException("Ballot not found"));
                yield TargetValidationStatus.VERIFIED;
            }
            case USER -> {
                if (targetId.equals(reporterId)) {
                    throw new InvalidRequestException("Users cannot report their own account");
                }
                if (!userRepository.existsById(targetId)) {
                    throw new ResourceNotFoundException("User not found");
                }
                yield TargetValidationStatus.VERIFIED;
            }
            case COMMENT -> {
                Comment comment = commentRepository.findReportableById(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
                if (comment.getAuthorId().equals(reporterId)) {
                    throw new InvalidRequestException("Users cannot report their own comment");
                }
                yield TargetValidationStatus.VERIFIED;
            }
        };
    }

    public void applyResolution(UUID actorId,
                                ModerationTargetType targetType,
                                UUID targetId,
                                ModerationResolutionAction action,
                                String reason,
                                Instant until) {
        if (action.targetType() != targetType) {
            throw new InvalidRequestException("Resolution action is incompatible with the moderation target");
        }
        switch (action) {
            case HIDE_BALLOT -> postModerationService.hide(actorId, targetId, reason);
            case RESTORE_BALLOT -> postModerationService.restore(actorId, targetId, reason);
            case DELETE_BALLOT -> postModerationService.softDelete(actorId, targetId, reason);
            case HIDE_COMMENT -> commentModerationService.hide(actorId, targetId, reason);
            case RESTORE_COMMENT -> commentModerationService.restore(actorId, targetId, reason);
            case DELETE_COMMENT -> commentModerationService.remove(actorId, targetId, reason);
            case SUSPEND_USER -> userModerationService.suspend(actorId, targetId, reason, until);
            case BAN_USER -> userModerationService.ban(actorId, targetId, reason, until);
            case RESTORE_USER -> userModerationService.restore(actorId, targetId, reason);
        }
    }
}
