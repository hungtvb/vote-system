package com.hungtvb.votesystem.admin.moderation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminModerationReasonRequest(
        @NotBlank
        @Size(max = 500)
        String reason
) {
}
