package com.hungtvb.votesystem.system.dto;

import com.hungtvb.votesystem.system.SystemMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UpdateSystemStatusRequest(
        @NotNull SystemMode mode,
        @Size(max = 200) String messageVi,
        @Size(max = 200) String messageEn,
        Instant estimatedEndAt,
        @NotBlank @Size(max = 500) String reason
) {
}
