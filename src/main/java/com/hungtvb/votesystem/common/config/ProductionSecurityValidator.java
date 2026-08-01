package com.hungtvb.votesystem.common.config;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@Profile("production")
public class ProductionSecurityValidator {
    private static final Set<String> INSECURE_SECRETS = Set.of(
            "dev-only-change-me-0123456789abcdef",
            "replace-this-with-at-least-32-random-characters",
            "runtime-smoke-change-me-0123456789abcdef"
    );

    public ProductionSecurityValidator(JwtProperties jwtProperties,
                                       RefreshTokenProperties refreshTokenProperties,
                                       CorsProperties corsProperties,
                                       Environment environment) {
        validateJwt(jwtProperties);
        validateRefreshCookie(refreshTokenProperties);
        validateCors(corsProperties.allowedOrigins());
        requireSecureOAuthCookie(environment);
    }

    private void validateJwt(JwtProperties properties) {
        String secret = properties.secret();
        if (secret == null
                || secret.getBytes(StandardCharsets.UTF_8).length < 32
                || INSECURE_SECRETS.contains(secret)
                || secret.toLowerCase(Locale.ROOT).contains("change-me")
                || secret.toLowerCase(Locale.ROOT).contains("replace-this")
                || secret.chars().distinct().count() < 12) {
            throw new IllegalStateException("Production JWT secret is missing or unsafe");
        }
        if (properties.issuer() == null || properties.issuer().isBlank()) {
            throw new IllegalStateException("Production JWT issuer must not be blank");
        }
    }

    private void validateRefreshCookie(RefreshTokenProperties properties) {
        if (!properties.secure()) {
            throw new IllegalStateException("Production refresh cookie must be Secure");
        }
        String sameSite = properties.sameSite();
        if (!"Strict".equalsIgnoreCase(sameSite) && !"Lax".equalsIgnoreCase(sameSite)) {
            throw new IllegalStateException("Production refresh cookie SameSite must be Strict or Lax");
        }
        if (!properties.cookieName().startsWith("__Secure-")) {
            throw new IllegalStateException("Production refresh cookie must use the __Secure- prefix");
        }
    }

    private void validateCors(List<String> origins) {
        if (origins == null || origins.isEmpty()) {
            throw new IllegalStateException("Production CORS allow-list must not be empty");
        }
        for (String origin : origins) {
            URI uri;
            try {
                uri = URI.create(origin);
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Production CORS origin is invalid", exception);
            }
            String host = uri.getHost();
            if (origin == null
                    || !origin.equals(origin.strip())
                    || !"https".equalsIgnoreCase(uri.getScheme())
                    || host == null
                    || host.isBlank()
                    || origin.contains("*")
                    || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                    || "localhost".equalsIgnoreCase(host)
                    || "0.0.0.0".equals(host)
                    || "::1".equals(host)
                    || host.startsWith("127.")) {
                throw new IllegalStateException("Production CORS origins must be canonical explicit HTTPS origins");
            }
        }
    }

    private void requireSecureOAuthCookie(Environment environment) {
        if (!environment.getProperty("server.servlet.session.cookie.secure", Boolean.class, false)) {
            throw new IllegalStateException("Production OAuth session cookie must be Secure");
        }
        String cookieName = environment.getProperty("server.servlet.session.cookie.name", "");
        if (!cookieName.startsWith("__Secure-")) {
            throw new IllegalStateException("Production OAuth session cookie must use the __Secure- prefix");
        }
    }
}
