package com.hungtvb.votesystem.moderation.dto;

import com.hungtvb.votesystem.moderation.ModerationTargetType;
import com.hungtvb.votesystem.moderation.ReportReasonCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateReportRequest(
        @NotNull ModerationTargetType targetType,
        @NotNull UUID targetId,
        @NotNull ReportReasonCode reasonCode,
        @NotBlank @Size(max = 1000) String evidenceText
) {
}
