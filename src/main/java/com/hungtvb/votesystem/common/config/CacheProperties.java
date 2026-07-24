package com.hungtvb.votesystem.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.cache")
public record CacheProperties(Duration defaultTtl) {
}
