package com.hungtvb.votesystem.user.dto;

import com.hungtvb.votesystem.user.AppUser;
import com.hungtvb.votesystem.user.AvatarColor;
import com.hungtvb.votesystem.user.AvatarIcon;
import com.hungtvb.votesystem.user.UserIdentityFormatter;

import java.time.Instant;
import java.util.UUID;

public record PublicUserProfileResponse(
        UUID id,
        String displayName,
        String initials,
        String bio,
        AvatarIcon avatarIcon,
        AvatarColor avatarColor,
        Instant createdAt
) {
    public static PublicUserProfileResponse from(AppUser user) {
        return new PublicUserProfileResponse(
                user.getId(),
                user.getDisplayName(),
                UserIdentityFormatter.initials(user.getDisplayName()),
                user.getBio(),
                user.getAvatarIcon(),
                user.getAvatarColor(),
                user.getCreatedAt()
        );
    }
}
