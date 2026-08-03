package com.hungtvb.votesystem.auth.session;

import com.hungtvb.votesystem.user.AppUser;

import java.util.UUID;

public record RefreshGrant(
        AppUser user,
        UUID sessionFamilyId,
        String refreshToken,
        long expiresInSeconds
) {
}
