package com.hungtvb.votesystem.auth.dto;

import java.util.UUID;

public record AuthResponse(
        String tokenType,
        String accessToken,
        long expiresInSeconds,
        UUID userId,
        String email,
        String role
) {
}
