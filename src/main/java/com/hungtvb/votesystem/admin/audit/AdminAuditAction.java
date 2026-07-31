package com.hungtvb.votesystem.admin.audit;

public enum AdminAuditAction {
    ADMIN_HIDE_POST(AdminAuditTargetType.POST),
    ADMIN_RESTORE_POST(AdminAuditTargetType.POST),
    ADMIN_DELETE_POST(AdminAuditTargetType.POST),
    ADMIN_SUSPEND_USER(AdminAuditTargetType.USER),
    ADMIN_BAN_USER(AdminAuditTargetType.USER),
    ADMIN_RESTORE_USER(AdminAuditTargetType.USER),
    ADMIN_REVOKE_SESSIONS(AdminAuditTargetType.USER),
    ADMIN_REBUILD_RANKING(AdminAuditTargetType.RANKING),
    ADMIN_CHANGE_SYSTEM_MODE(AdminAuditTargetType.SYSTEM);

    private final AdminAuditTargetType targetType;

    AdminAuditAction(AdminAuditTargetType targetType) {
        this.targetType = targetType;
    }

    public AdminAuditTargetType targetType() {
        return targetType;
    }
}
