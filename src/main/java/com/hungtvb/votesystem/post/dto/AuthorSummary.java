package com.hungtvb.votesystem.post.dto;

import com.hungtvb.votesystem.user.AppUser;
import com.hungtvb.votesystem.user.UserIdentityFormatter;

import java.util.UUID;

public record AuthorSummary(
        UUID id,
        String displayName,
        String initials
) {
    public static AuthorSummary from(AppUser user) {
        String displayName = UserIdentityFormatter.displayName(user.getEmail());
        return new AuthorSummary(
                user.getId(),
                displayName,
                UserIdentityFormatter.initials(displayName)
        );
    }

    public static AuthorSummary technical(UUID authorId) {
        String suffix = authorId.toString().substring(0, 8).toUpperCase();
        return new AuthorSummary(authorId, "Voter " + suffix, "V");
    }
}
