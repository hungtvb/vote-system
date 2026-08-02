package com.hungtvb.votesystem.moderation.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReportHistoryResponse(
        List<ReportResponse> content,
        Instant nextBeforeCreatedAt,
        UUID nextBeforeId,
        boolean hasMore
) {
}
