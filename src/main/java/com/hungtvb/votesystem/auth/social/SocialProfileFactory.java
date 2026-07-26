package com.hungtvb.votesystem.auth.social;

import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;

public final class SocialProfileFactory {
    private SocialProfileFactory() {
    }

    public static SocialProfile from(SocialProvider provider, OAuth2User principal) {
        return switch (provider) {
            case GOOGLE -> google(principal);
            case GITHUB -> github(principal);
        };
    }

    private static SocialProfile google(OAuth2User principal) {
        if (!(principal instanceof OidcUser oidcUser)) {
            throw new SocialLoginException("invalid_google_identity", "Google did not return an OpenID Connect identity");
        }
        return new SocialProfile(
                SocialProvider.GOOGLE,
                oidcUser.getSubject(),
                oidcUser.getEmail(),
                Boolean.TRUE.equals(oidcUser.getEmailVerified()),
                oidcUser.getFullName());
    }

    private static SocialProfile github(OAuth2User principal) {
        Object id = principal.getAttribute("id");
        String login = principal.getAttribute("login");
        String name = principal.getAttribute("name");
        String email = principal.getAttribute("email");
        return new SocialProfile(
                SocialProvider.GITHUB,
                id == null ? null : id.toString(),
                email,
                false,
                name == null || name.isBlank() ? login : name);
    }
}
