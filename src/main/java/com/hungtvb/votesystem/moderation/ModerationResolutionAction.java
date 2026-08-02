package com.hungtvb.votesystem.moderation;

public enum ModerationResolutionAction {
    HIDE_BALLOT(ModerationTargetType.BALLOT),
    RESTORE_BALLOT(ModerationTargetType.BALLOT),
    DELETE_BALLOT(ModerationTargetType.BALLOT),
    SUSPEND_USER(ModerationTargetType.USER),
    BAN_USER(ModerationTargetType.USER),
    RESTORE_USER(ModerationTargetType.USER);

    private final ModerationTargetType targetType;

    ModerationResolutionAction(ModerationTargetType targetType) {
        this.targetType = targetType;
    }

    public ModerationTargetType targetType() {
        return targetType;
    }
}
