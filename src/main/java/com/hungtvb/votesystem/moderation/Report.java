package com.hungtvb.votesystem.moderation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "reports")
public class Report {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "case_id", nullable = false, updatable = false)
    private UUID caseId;

    @Column(name = "reporter_id", nullable = false, updatable = false)
    private UUID reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, updatable = false, length = 16)
    private ModerationTargetType targetType;

    @Column(name = "target_id", nullable = false, updatable = false)
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, updatable = false, length = 32)
    private ReportReasonCode reasonCode;

    @Column(name = "evidence_text", nullable = false, updatable = false, length = 1000)
    private String evidenceText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReportStatus status;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Report() {
    }

    private Report(UUID caseId,
                   UUID reporterId,
                   ModerationTargetType targetType,
                   UUID targetId,
                   ReportReasonCode reasonCode,
                   String evidenceText,
                   Instant now) {
        this.caseId = Objects.requireNonNull(caseId);
        this.reporterId = Objects.requireNonNull(reporterId);
        this.targetType = Objects.requireNonNull(targetType);
        this.targetId = Objects.requireNonNull(targetId);
        this.reasonCode = Objects.requireNonNull(reasonCode);
        this.evidenceText = Objects.requireNonNull(evidenceText);
        this.status = ReportStatus.OPEN;
        this.createdAt = now;
    }

    public static Report create(UUID caseId,
                                UUID reporterId,
                                ModerationTargetType targetType,
                                UUID targetId,
                                ReportReasonCode reasonCode,
                                String evidenceText,
                                Instant now) {
        return new Report(caseId, reporterId, targetType, targetId, reasonCode, evidenceText, now);
    }

    public UUID getId() { return id; }
    public UUID getCaseId() { return caseId; }
    public UUID getReporterId() { return reporterId; }
    public ModerationTargetType getTargetType() { return targetType; }
    public UUID getTargetId() { return targetId; }
    public ReportReasonCode getReasonCode() { return reasonCode; }
    public String getEvidenceText() { return evidenceText; }
    public ReportStatus getStatus() { return status; }
    public Instant getClosedAt() { return closedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
