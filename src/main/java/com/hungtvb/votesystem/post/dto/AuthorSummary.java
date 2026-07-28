package com.hungtvb.votesystem.post.dto;

import com.hungtvb.votesystem.user.AppUser;
import com.hungtvb.votesystem.user.AvatarColor;
import com.hungtvb.votesystem.user.AvatarIcon;
import com.hungtvb.votesystem.user.UserIdentityFormatter;

import java.util.Locale;
import java.util.UUID;

public record AuthorSummary(
        UUID id,
        String displayName,
        String initials,
        AvatarIcon avatarIcon,
        AvatarColor avatarColor
) {
    public static AuthorSummary from(AppUser user) {
        return new AuthorSummary(
                user.getId(),
                user.getDisplayName(),
                UserIdentityFormatter.initials(user.getDisplayName()),
                user.getAvatarIcon(),
                user.getAvatarColor()
        );
    }

    public static AuthorSummary technical(UUID authorId) {
        String suffix = authorId.toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        String displayName = "Voter " + suffix;
        return new AuthorSummary(
                authorId,
                displayName,
                UserIdentityFormatter.initials(displayName),
                AvatarIcon.CITIZEN,
                AvatarColor.NAVY
        );
    }
}
