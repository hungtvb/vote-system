package com.hungtvb.votesystem.admin.audit;

import java.util.UUID;

public record AdminAuditLogFilter(
        AdminAuditAction action,
        UUID actorId,
        AdminAuditTargetType targetType,
        String targetId
) {
}
