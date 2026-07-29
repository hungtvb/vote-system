package com.hungtvb.votesystem.auth.dto;

import com.hungtvb.votesystem.user.dto.UserProfileResponse;

import java.util.UUID;

public record AuthResponse(
        String tokenType,
        String accessToken,
        long expiresInSeconds,
        long refreshExpiresInSeconds,
        UUID userId,
        String email,
        String role,
        UserProfileResponse profile
) {
}
