package com.hungtvb.votesystem.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.admin-bootstrap")
public record AdminBootstrapProperties(boolean enabled, String email) {
}
