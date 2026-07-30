package com.hungtvb.votesystem.admin.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Immutable
@Entity
@Table(name = "admin_audit_logs")
public class AdminAuditLog {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 64)
    private AdminAuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, updatable = false, length = 32)
    private AdminAuditTargetType targetType;

    @Column(name = "target_id", nullable = false, updatable = false, length = 128)
    private String targetId;

    @Column(nullable = false, updatable = false, length = 500)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, String> metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AdminAuditLog() {
    }

    private AdminAuditLog(UUID actorId,
                          AdminAuditAction action,
                          AdminAuditTargetType targetType,
                          String targetId,
                          String reason,
                          Map<String, String> metadata,
                          Instant createdAt) {
        this.id = UUID.randomUUID();
        this.actorId = actorId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.metadata = new LinkedHashMap<>(metadata);
        this.createdAt = createdAt;
    }

    static AdminAuditLog create(UUID actorId,
                                AdminAuditAction action,
                                AdminAuditTargetType targetType,
                                String targetId,
                                String reason,
                                Map<String, String> metadata,
                                Instant createdAt) {
        return new AdminAuditLog(actorId, action, targetType, targetId, reason, metadata, createdAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getActorId() {
        return actorId;
    }

    public AdminAuditAction getAction() {
        return action;
    }

    public AdminAuditTargetType getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getReason() {
        return reason;
    }

    public Map<String, String> getMetadata() {
        return Map.copyOf(metadata);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
