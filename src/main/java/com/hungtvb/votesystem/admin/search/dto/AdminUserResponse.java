package com.hungtvb.votesystem.admin.search.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hungtvb.votesystem.auth.social.SocialProvider;
import com.hungtvb.votesystem.user.AccountStatus;
import com.hungtvb.votesystem.user.AppUser;
import com.hungtvb.votesystem.user.Role;
import com.hungtvb.votesystem.user.UserIdentityFormatter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminUserResponse(
        UUID id,
        String email,
        String displayName,
        String initials,
        Role role,
        AccountStatus accountStatus,
        Instant statusUntil,
        Instant statusUpdatedAt,
        List<SocialProvider> linkedProviders,
        Instant createdAt,
        Instant updatedAt
) {
    public static AdminUserResponse from(AppUser user,
                                         List<SocialProvider> linkedProviders,
                                         Instant now) {
        AccountStatus effectiveStatus = user.effectiveAccountStatus(now);
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                UserIdentityFormatter.initials(user.getDisplayName()),
                user.getRole(),
                effectiveStatus,
                effectiveStatus == AccountStatus.ACTIVE ? null : user.getStatusUntil(),
                user.getStatusUpdatedAt(),
                linkedProviders,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
