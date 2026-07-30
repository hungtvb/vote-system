package com.hungtvb.votesystem.admin.usermoderation.dto;

import com.hungtvb.votesystem.user.AccountStatus;
import com.hungtvb.votesystem.user.AppUser;

import java.time.Instant;
import java.util.UUID;

public record AdminUserModerationResponse(
        UUID id,
        AccountStatus accountStatus,
        Instant statusUntil,
        Instant statusUpdatedAt,
        int revokedSessions
) {
    public static AdminUserModerationResponse from(AppUser user, int revokedSessions) {
        return new AdminUserModerationResponse(
                user.getId(),
                user.getAccountStatus(),
                user.getStatusUntil(),
                user.getStatusUpdatedAt(),
                revokedSessions
        );
    }
}
