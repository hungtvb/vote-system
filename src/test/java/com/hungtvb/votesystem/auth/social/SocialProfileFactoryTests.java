package com.hungtvb.votesystem.auth.social;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialProfileFactoryTests {

    @Test
    void googleOidcClaimsMapToVerifiedProfile() {
        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken(
                "google-id-token",
                now,
                now.plusSeconds(300),
                Map.of(
                        "sub", "google-subject-1001",
                        "email", "google-voter@example.com",
                        "email_verified", true,
                        "name", "Google Voter"
                ));
        DefaultOidcUser principal = new DefaultOidcUser(
                Set.of(new OidcUserAuthority(idToken)),
                idToken);

        SocialProfile profile = SocialProfileFactory.from(SocialProvider.GOOGLE, principal);

        assertEquals(SocialProvider.GOOGLE, profile.provider());
        assertEquals("google-subject-1001", profile.subject());
        assertEquals("google-voter@example.com", profile.email());
        assertTrue(profile.emailVerified());
        assertEquals("Google Voter", profile.displayName());
    }

    @Test
    void githubPrivateEmailUsesDurableSubjectAndLoginFallback() {
        DefaultOAuth2User principal = new DefaultOAuth2User(
                Set.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("id", 202L, "login", "private-octocat"),
                "id");

        SocialProfile profile = SocialProfileFactory.from(SocialProvider.GITHUB, principal);

        assertEquals(SocialProvider.GITHUB, profile.provider());
        assertEquals("202", profile.subject());
        assertNull(profile.email());
        assertFalse(profile.emailVerified());
        assertEquals("private-octocat", profile.displayName());
    }

    @Test
    void malformedGithubProfileIsRejectedBeforePersistence() {
        DefaultOAuth2User principal = new DefaultOAuth2User(
                Set.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("login", "missing-subject"),
                "login");

        assertThrows(IllegalArgumentException.class,
                () -> SocialProfileFactory.from(SocialProvider.GITHUB, principal));
    }
}
