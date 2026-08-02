package com.hungtvb.votesystem.moderation;

public enum ModerationCaseStatus {
    OPEN,
    TRIAGED,
    IN_REVIEW,
    RESOLVED,
    REJECTED,
    REOPENED;

    public boolean isActive() {
        return this == OPEN || this == TRIAGED || this == IN_REVIEW || this == REOPENED;
    }
}
