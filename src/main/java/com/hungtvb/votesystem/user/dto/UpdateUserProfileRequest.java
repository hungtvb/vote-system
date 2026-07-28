package com.hungtvb.votesystem.user.dto;

import com.hungtvb.votesystem.user.AvatarColor;
import com.hungtvb.votesystem.user.AvatarIcon;
import com.hungtvb.votesystem.user.PreferredLocale;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
        @NotBlank @Size(min = 2, max = 40) String displayName,
        @Size(max = 160) String bio,
        @NotNull AvatarIcon avatarIcon,
        @NotNull AvatarColor avatarColor,
        @NotNull PreferredLocale preferredLocale
) {
}
