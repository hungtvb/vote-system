package com.hungtvb.votesystem.auth;

import com.hungtvb.votesystem.auth.dto.AuthResponse;

public record IssuedAuthSession(
        AuthResponse response,
        String refreshToken
) {
}
