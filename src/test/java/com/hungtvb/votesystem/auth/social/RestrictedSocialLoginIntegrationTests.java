package com.hungtvb.votesystem.auth.social;

import com.hungtvb.votesystem.common.error.UnauthorizedException;
import com.hungtvb.votesystem.user.AccountStatus;
import com.hungtvb.votesystem.user.AppUser;
import com.hungtvb.votesystem.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(properties = {
        "app.rate-limit.enabled=false",
        "app.social-login.success-url=/",
        "app.social-login.failure-url=/"
})
class RestrictedSocialLoginIntegrationTests {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureInfrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.url", () -> "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
    }

    @Autowired SocialLoginService socialLoginService;
    @Autowired SocialAuthenticationSuccessHandler successHandler;
    @Autowired UserIdentityRepository identityRepository;
    @Autowired UserRepository userRepository;

    @Test
    void restrictedExistingIdentityCannotCompleteSocialLoginOrReceiveRefreshCookie() throws Exception {
        SocialProfile profile = new SocialProfile(
                SocialProvider.GITHUB,
                "901",
                null,
                false,
                "Restricted Octocat"
        );
        AppUser user = socialLoginService.complete(
                profile,
                SocialAuthContext.authenticate(SocialIntent.AUTHENTICATE)
        );
        user.restrict(AccountStatus.SUSPENDED, null, Instant.now());
        userRepository.saveAndFlush(user);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                SocialAuthContext.SESSION_ATTRIBUTE,
                SocialAuthContext.authenticate(SocialIntent.AUTHENTICATE)
        );
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        DefaultOAuth2User principal = new DefaultOAuth2User(
                Set.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("id", 901L, "login", "restricted-octocat", "name", "Restricted Octocat"),
                "id"
        );
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
                principal,
                principal.getAuthorities(),
                "github"
        );

        successHandler.onAuthenticationSuccess(request, response, authentication);

        assertEquals("/?social=error&code=account_unavailable&intent=authenticate", response.getRedirectedUrl());
        assertNull(response.getHeader("Set-Cookie"));
        assertEquals(1, identityRepository.findAllByUserId(user.getId()).size());
    }

    @Test
    void restrictedLinkContextDoesNotPersistANewProviderIdentity() {
        AppUser user = userRepository.saveAndFlush(AppUser.create(
                "restricted-link@example.com",
                "Restricted Link",
                "password-hash"
        ));
        user.restrict(AccountStatus.BANNED, null, Instant.now());
        userRepository.saveAndFlush(user);
        SocialProfile profile = new SocialProfile(
                SocialProvider.GOOGLE,
                "restricted-link-902",
                "restricted-link@example.com",
                true,
                "Restricted Link"
        );

        assertThrows(UnauthorizedException.class, () -> socialLoginService.complete(
                profile,
                SocialAuthContext.link(user.getId())
        ));

        assertTrue(identityRepository
                .findByProviderAndProviderSubject(SocialProvider.GOOGLE, "restricted-link-902")
                .isEmpty());
        assertTrue(identityRepository.findAllByUserId(user.getId()).isEmpty());
    }
}
