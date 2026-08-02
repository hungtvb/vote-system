package com.hungtvb.votesystem.moderation.admin.dto;

import com.hungtvb.votesystem.moderation.ModerationCase;
import com.hungtvb.votesystem.moderation.ModerationCaseStatus;
import com.hungtvb.votesystem.moderation.ModerationResolutionAction;
import com.hungtvb.votesystem.moderation.ModerationTargetType;
import com.hungtvb.votesystem.moderation.TargetValidationStatus;

import java.time.Instant;
import java.util.UUID;

public record ModerationCaseResponse(
        UUID id,
        ModerationTargetType targetType,
        UUID targetId,
        TargetValidationStatus targetValidationStatus,
        ModerationCaseStatus status,
        UUID assigneeId,
        int reportCount,
        ModerationResolutionAction resolutionAction,
        String resolutionReason,
        Instant resolutionUntil,
        Instant resolvedAt,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public static ModerationCaseResponse from(ModerationCase moderationCase) {
        return new ModerationCaseResponse(
                moderationCase.getId(),
                moderationCase.getTargetType(),
                moderationCase.getTargetId(),
                moderationCase.getTargetValidationStatus(),
                moderationCase.getStatus(),
                moderationCase.getAssigneeId(),
                moderationCase.getReportCount(),
                moderationCase.getResolutionAction(),
                moderationCase.getResolutionReason(),
                moderationCase.getResolutionUntil(),
                moderationCase.getResolvedAt(),
                moderationCase.getCreatedAt(),
                moderationCase.getUpdatedAt(),
                moderationCase.getVersion()
        );
    }
}
