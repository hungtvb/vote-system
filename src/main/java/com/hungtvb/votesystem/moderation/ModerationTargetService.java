package com.hungtvb.votesystem.moderation;

import com.hungtvb.votesystem.admin.moderation.AdminPostModerationService;
import com.hungtvb.votesystem.admin.usermoderation.AdminUserModerationService;
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
    private final AdminPostModerationService postModerationService;
    private final AdminUserModerationService userModerationService;

    public ModerationTargetService(PostRepository postRepository,
                                   UserRepository userRepository,
                                   AdminPostModerationService postModerationService,
                                   AdminUserModerationService userModerationService) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.postModerationService = postModerationService;
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
            case COMMENT -> TargetValidationStatus.DEFERRED;
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
            case SUSPEND_USER -> userModerationService.suspend(actorId, targetId, reason, until);
            case BAN_USER -> userModerationService.ban(actorId, targetId, reason, until);
            case RESTORE_USER -> userModerationService.restore(actorId, targetId, reason);
        }
    }
}
