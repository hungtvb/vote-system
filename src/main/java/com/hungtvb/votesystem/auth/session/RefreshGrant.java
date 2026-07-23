package com.hungtvb.votesystem.auth.session;

import com.hungtvb.votesystem.security.AuthenticatedUser;

public record RefreshGrant(
        AuthenticatedUser user,
        String refreshToken,
        long expiresInSeconds
) {
}
