package com.hungtvb.votesystem.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(String issuer, String secret, Duration accessTokenTtl) {
}
