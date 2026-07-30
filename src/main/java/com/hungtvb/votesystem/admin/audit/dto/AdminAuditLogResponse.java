package com.hungtvb.votesystem.admin.audit.dto;

import com.hungtvb.votesystem.admin.audit.AdminAuditAction;
import com.hungtvb.votesystem.admin.audit.AdminAuditLog;
import com.hungtvb.votesystem.admin.audit.AdminAuditTargetType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AdminAuditLogResponse(
        UUID id,
        UUID actorId,
        AdminAuditAction action,
        AdminAuditTargetType targetType,
        String targetId,
        String reason,
        Map<String, String> metadata,
        Instant createdAt
) {
    public static AdminAuditLogResponse from(AdminAuditLog auditLog) {
        return new AdminAuditLogResponse(
                auditLog.getId(),
                auditLog.getActorId(),
                auditLog.getAction(),
                auditLog.getTargetType(),
                auditLog.getTargetId(),
                auditLog.getReason(),
                auditLog.getMetadata(),
                auditLog.getCreatedAt()
        );
    }
}
