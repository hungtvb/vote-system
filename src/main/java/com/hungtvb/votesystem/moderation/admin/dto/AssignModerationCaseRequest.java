package com.hungtvb.votesystem.moderation.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AssignModerationCaseRequest(
        UUID assigneeId,
        @NotBlank @Size(max = 500) String reason
) {
}
