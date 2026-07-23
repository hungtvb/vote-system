package com.hungtvb.votesystem.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.security.refresh-token")
public record RefreshTokenProperties(
        Duration ttl,
        String cookieName,
        boolean secure,
        String sameSite
) {
    public RefreshTokenProperties {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("Refresh token TTL must be positive");
        }
        if (cookieName == null || cookieName.isBlank()) {
            throw new IllegalArgumentException("Refresh token cookie name must not be blank");
        }
        if (sameSite == null || sameSite.isBlank()) {
            throw new IllegalArgumentException("Refresh token SameSite policy must not be blank");
        }
    }
}
