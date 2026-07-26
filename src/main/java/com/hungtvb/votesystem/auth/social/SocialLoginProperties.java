package com.hungtvb.votesystem.auth.social;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.social-login")
public record SocialLoginProperties(
        String successUrl,
        String failureUrl,
        ProviderCredentials google,
        ProviderCredentials github
) {
    public SocialLoginProperties {
        successUrl = defaultValue(successUrl, "/");
        failureUrl = defaultValue(failureUrl, "/");
        google = google == null ? new ProviderCredentials("", "") : google;
        github = github == null ? new ProviderCredentials("", "") : github;
    }

    public ProviderCredentials credentials(SocialProvider provider) {
        return provider == SocialProvider.GOOGLE ? google : github;
    }

    private static String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public record ProviderCredentials(String clientId, String clientSecret) {
        public ProviderCredentials {
            clientId = clientId == null ? "" : clientId.trim();
            clientSecret = clientSecret == null ? "" : clientSecret.trim();
        }

        public boolean enabled() {
            return !clientId.isBlank() && !clientSecret.isBlank();
        }
    }
}
