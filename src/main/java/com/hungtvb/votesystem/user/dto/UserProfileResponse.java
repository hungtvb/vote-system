package com.hungtvb.votesystem.user.dto;

import com.hungtvb.votesystem.user.AppUser;
import com.hungtvb.votesystem.user.Role;
import com.hungtvb.votesystem.user.UserIdentityFormatter;

import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String email,
        String displayName,
        String initials,
        Role role,
        Instant createdAt,
        Instant updatedAt
) {
    public static UserProfileResponse from(AppUser user) {
        String displayName = UserIdentityFormatter.displayName(user.getEmail());
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                displayName,
                UserIdentityFormatter.initials(displayName),
                user.getRole(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
