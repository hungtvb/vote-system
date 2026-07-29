package com.hungtvb.votesystem.auth.session;

import com.hungtvb.votesystem.user.AppUser;

public record RefreshGrant(
        AppUser user,
        String refreshToken,
        long expiresInSeconds
) {
}
