package com.hungtvb.votesystem.moderation.dto;

import com.hungtvb.votesystem.moderation.ModerationCase;
import com.hungtvb.votesystem.moderation.ModerationCaseStatus;
import com.hungtvb.votesystem.moderation.ModerationTargetType;
import com.hungtvb.votesystem.moderation.Report;
import com.hungtvb.votesystem.moderation.ReportReasonCode;
import com.hungtvb.votesystem.moderation.ReportStatus;
import com.hungtvb.votesystem.moderation.TargetValidationStatus;

import java.time.Instant;
import java.util.UUID;

public record ReportResponse(
        UUID id,
        UUID caseId,
        ModerationTargetType targetType,
        UUID targetId,
        TargetValidationStatus targetValidationStatus,
        ReportReasonCode reasonCode,
        String evidenceText,
        ReportStatus status,
        ModerationCaseStatus caseStatus,
        Instant closedAt,
        Instant createdAt
) {
    public static ReportResponse from(Report report, ModerationCase moderationCase) {
        return new ReportResponse(
                report.getId(),
                report.getCaseId(),
                report.getTargetType(),
                report.getTargetId(),
                moderationCase.getTargetValidationStatus(),
                report.getReasonCode(),
                report.getEvidenceText(),
                report.getStatus(),
                moderationCase.getStatus(),
                report.getClosedAt(),
                report.getCreatedAt()
        );
    }

    public static ReportResponse history(Report report, ModerationCase moderationCase) {
        return from(report, moderationCase);
    }
}
