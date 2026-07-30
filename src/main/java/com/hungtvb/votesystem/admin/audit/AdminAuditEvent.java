package com.hungtvb.votesystem.admin.audit;

import java.util.Map;
import java.util.UUID;

public record AdminAuditEvent(
        UUID actorId,
        AdminAuditAction action,
        AdminAuditTargetType targetType,
        String targetId,
        String reason,
        Map<String, String> metadata
) {
}
