package com.hungtvb.votesystem.moderation.admin.dto;

import java.util.List;

public record ModerationCaseDetailResponse(
        ModerationCaseResponse moderationCase,
        List<AdminReportResponse> reports
) {
}
