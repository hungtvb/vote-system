package com.hungtvb.votesystem.moderation.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ModerationCaseReasonRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
