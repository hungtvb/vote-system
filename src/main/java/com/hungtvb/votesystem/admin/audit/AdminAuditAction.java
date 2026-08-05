package com.hungtvb.votesystem.admin.audit;

public enum AdminAuditAction {
    ADMIN_HIDE_POST(AdminAuditTargetType.POST),
    ADMIN_RESTORE_POST(AdminAuditTargetType.POST),
    ADMIN_DELETE_POST(AdminAuditTargetType.POST),
    ADMIN_HIDE_COMMENT(AdminAuditTargetType.COMMENT),
    ADMIN_RESTORE_COMMENT(AdminAuditTargetType.COMMENT),
    ADMIN_DELETE_COMMENT(AdminAuditTargetType.COMMENT),
    ADMIN_SUSPEND_USER(AdminAuditTargetType.USER),
    ADMIN_BAN_USER(AdminAuditTargetType.USER),
    ADMIN_RESTORE_USER(AdminAuditTargetType.USER),
    ADMIN_REVOKE_SESSIONS(AdminAuditTargetType.USER),
    ADMIN_REBUILD_RANKING(AdminAuditTargetType.RANKING),
    SYSTEM_MODE_CHANGED(AdminAuditTargetType.SYSTEM),
    ADMIN_ASSIGN_MODERATION_CASE(AdminAuditTargetType.MODERATION_CASE),
    ADMIN_TRIAGE_MODERATION_CASE(AdminAuditTargetType.MODERATION_CASE),
    ADMIN_REVIEW_MODERATION_CASE(AdminAuditTargetType.MODERATION_CASE),
    ADMIN_RESOLVE_MODERATION_CASE(AdminAuditTargetType.MODERATION_CASE),
    ADMIN_REJECT_MODERATION_CASE(AdminAuditTargetType.MODERATION_CASE),
    ADMIN_REOPEN_MODERATION_CASE(AdminAuditTargetType.MODERATION_CASE);

    private final AdminAuditTargetType targetType;

    AdminAuditAction(AdminAuditTargetType targetType) {
        this.targetType = targetType;
    }

    public AdminAuditTargetType targetType() {
        return targetType;
    }
}
