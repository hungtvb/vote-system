package com.hungtvb.votesystem.moderation.admin.dto;

import com.hungtvb.votesystem.moderation.Report;
import com.hungtvb.votesystem.moderation.ReportReasonCode;
import com.hungtvb.votesystem.moderation.ReportStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminReportResponse(
        UUID id,
        UUID reporterId,
        ReportReasonCode reasonCode,
        String evidenceText,
        ReportStatus status,
        Instant closedAt,
        Instant createdAt
) {
    public static AdminReportResponse from(Report report) {
        return new AdminReportResponse(
                report.getId(),
                report.getReporterId(),
                report.getReasonCode(),
                report.getEvidenceText(),
                report.getStatus(),
                report.getClosedAt(),
                report.getCreatedAt()
        );
    }
}
