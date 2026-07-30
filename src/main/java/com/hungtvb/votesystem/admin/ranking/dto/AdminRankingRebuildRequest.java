package com.hungtvb.votesystem.admin.ranking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminRankingRebuildRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
