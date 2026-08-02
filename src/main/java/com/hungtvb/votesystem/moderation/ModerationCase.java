package com.hungtvb.votesystem.moderation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "moderation_cases")
public class ModerationCase {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, updatable = false, length = 16)
    private ModerationTargetType targetType;

    @Column(name = "target_id", nullable = false, updatable = false)
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_validation_status", nullable = false, length = 16)
    private TargetValidationStatus targetValidationStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ModerationCaseStatus status;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "report_count", nullable = false)
    private int reportCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_action", length = 32)
    private ModerationResolutionAction resolutionAction;

    @Column(name = "resolution_reason", length = 500)
    private String resolutionReason;

    @Column(name = "resolution_until")
    private Instant resolutionUntil;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ModerationCase() {
    }

    private ModerationCase(ModerationTargetType targetType,
                           UUID targetId,
                           TargetValidationStatus validationStatus,
                           Instant now) {
        this.targetType = Objects.requireNonNull(targetType);
        this.targetId = Objects.requireNonNull(targetId);
        this.targetValidationStatus = Objects.requireNonNull(validationStatus);
        this.status = ModerationCaseStatus.OPEN;
        this.reportCount = 1;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static ModerationCase open(ModerationTargetType targetType,
                                      UUID targetId,
                                      TargetValidationStatus validationStatus,
                                      Instant now) {
        return new ModerationCase(targetType, targetId, validationStatus, now);
    }

    public void attachReport(TargetValidationStatus validationStatus, Instant now) {
        requireActive();
        reportCount = Math.incrementExact(reportCount);
        if (validationStatus == TargetValidationStatus.VERIFIED) {
            targetValidationStatus = TargetValidationStatus.VERIFIED;
        }
        updatedAt = now;
    }

    public boolean assign(UUID requestedAssigneeId, Instant now) {
        requireActive();
        if (Objects.equals(assigneeId, requestedAssigneeId)) {
            return false;
        }
        assigneeId = Objects.requireNonNull(requestedAssigneeId);
        updatedAt = now;
        return true;
    }

    public boolean triage(Instant now) {
        if (status == ModerationCaseStatus.TRIAGED) {
            return false;
        }
        if (status != ModerationCaseStatus.OPEN && status != ModerationCaseStatus.REOPENED) {
            throw new IllegalStateException("Only open or reopened cases can be triaged");
        }
        status = ModerationCaseStatus.TRIAGED;
        updatedAt = now;
        return true;
    }

    public boolean beginReview(Instant now) {
        if (status == ModerationCaseStatus.IN_REVIEW) {
            return false;
        }
        if (status != ModerationCaseStatus.TRIAGED) {
            throw new IllegalStateException("Only triaged cases can enter review");
        }
        status = ModerationCaseStatus.IN_REVIEW;
        updatedAt = now;
        return true;
    }

    public boolean isSameResolution(ModerationResolutionAction action, String reason, Instant until) {
        return status == ModerationCaseStatus.RESOLVED
                && resolutionAction == action
                && Objects.equals(resolutionReason, reason)
                && Objects.equals(resolutionUntil, until);
    }

    public void resolve(ModerationResolutionAction action, String reason, Instant until, Instant now) {
        if (status != ModerationCaseStatus.IN_REVIEW) {
            throw new IllegalStateException("Only cases in review can be resolved");
        }
        resolutionAction = Objects.requireNonNull(action);
        resolutionReason = Objects.requireNonNull(reason);
        resolutionUntil = until;
        resolvedAt = now;
        status = ModerationCaseStatus.RESOLVED;
        updatedAt = now;
    }

    public boolean isSameRejection(String reason) {
        return status == ModerationCaseStatus.REJECTED && Objects.equals(resolutionReason, reason);
    }

    public void reject(String reason, Instant now) {
        if (status != ModerationCaseStatus.IN_REVIEW) {
            throw new IllegalStateException("Only cases in review can be rejected");
        }
        resolutionAction = null;
        resolutionReason = Objects.requireNonNull(reason);
        resolutionUntil = null;
        resolvedAt = now;
        status = ModerationCaseStatus.REJECTED;
        updatedAt = now;
    }

    public boolean reopen(Instant now) {
        if (status == ModerationCaseStatus.REOPENED) {
            return false;
        }
        if (status != ModerationCaseStatus.RESOLVED && status != ModerationCaseStatus.REJECTED) {
            throw new IllegalStateException("Only resolved or rejected cases can be reopened");
        }
        status = ModerationCaseStatus.REOPENED;
        assigneeId = null;
        resolutionAction = null;
        resolutionReason = null;
        resolutionUntil = null;
        resolvedAt = null;
        updatedAt = now;
        return true;
    }

    private void requireActive() {
        if (!status.isActive()) {
            throw new IllegalStateException("Moderation case is closed");
        }
    }

    public UUID getId() { return id; }
    public ModerationTargetType getTargetType() { return targetType; }
    public UUID getTargetId() { return targetId; }
    public TargetValidationStatus getTargetValidationStatus() { return targetValidationStatus; }
    public ModerationCaseStatus getStatus() { return status; }
    public UUID getAssigneeId() { return assigneeId; }
    public int getReportCount() { return reportCount; }
    public ModerationResolutionAction getResolutionAction() { return resolutionAction; }
    public String getResolutionReason() { return resolutionReason; }
    public Instant getResolutionUntil() { return resolutionUntil; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
