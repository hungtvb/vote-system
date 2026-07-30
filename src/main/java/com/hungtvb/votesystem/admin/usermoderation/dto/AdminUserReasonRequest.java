package com.hungtvb.votesystem.admin.usermoderation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminUserReasonRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
