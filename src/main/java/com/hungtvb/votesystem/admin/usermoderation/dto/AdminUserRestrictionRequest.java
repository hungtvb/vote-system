package com.hungtvb.votesystem.admin.usermoderation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record AdminUserRestrictionRequest(
        @NotBlank @Size(max = 500) String reason,
        Instant until
) {
}
