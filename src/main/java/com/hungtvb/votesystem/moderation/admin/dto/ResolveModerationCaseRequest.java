package com.hungtvb.votesystem.moderation.admin.dto;

import com.hungtvb.votesystem.moderation.ModerationResolutionAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record ResolveModerationCaseRequest(
        @NotNull ModerationResolutionAction action,
        @NotBlank @Size(max = 500) String reason,
        Instant until
) {
}
