package com.hungtvb.votesystem.user.dto;

import com.hungtvb.votesystem.auth.social.SocialProvider;
import com.hungtvb.votesystem.auth.social.UserIdentity;
import com.hungtvb.votesystem.user.AppUser;
import com.hungtvb.votesystem.user.AvatarColor;
import com.hungtvb.votesystem.user.AvatarIcon;
import com.hungtvb.votesystem.user.PreferredLocale;
import com.hungtvb.votesystem.user.Role;
import com.hungtvb.votesystem.user.UserIdentityFormatter;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record UserProfileResponse(
        UUID id,
        String email,
        String displayName,
        String initials,
        String bio,
        AvatarIcon avatarIcon,
        AvatarColor avatarColor,
        PreferredLocale preferredLocale,
        Role role,
        Set<SocialProvider> linkedProviders,
        Instant createdAt,
        Instant updatedAt
) {
    public static UserProfileResponse from(AppUser user, List<UserIdentity> identities) {
        Set<SocialProvider> providers = identities.stream()
                .map(UserIdentity::getProvider)
                .collect(Collectors.toUnmodifiableSet());
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                UserIdentityFormatter.initials(user.getDisplayName()),
                user.getBio(),
                user.getAvatarIcon(),
                user.getAvatarColor(),
                user.getPreferredLocale(),
                user.getRole(),
                providers,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
