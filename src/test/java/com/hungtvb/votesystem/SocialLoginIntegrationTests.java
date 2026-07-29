package com.hungtvb.votesystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hungtvb.votesystem.auth.social.SocialAuthContext;
import com.hungtvb.votesystem.auth.social.SocialAuthenticationSuccessHandler;
import com.hungtvb.votesystem.auth.social.SocialIntent;
import com.hungtvb.votesystem.auth.social.SocialLoginException;
import com.hungtvb.votesystem.auth.social.SocialLoginService;
import com.hungtvb.votesystem.auth.social.SocialProfile;
import com.hungtvb.votesystem.auth.social.SocialProvider;
import com.hungtvb.votesystem.auth.social.UserIdentityRepository;
import com.hungtvb.votesystem.user.AppUser;
import com.hungtvb.votesystem.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {
        "app.rate-limit.enabled=false",
        "app.social-login.success-url=/",
        "app.social-login.failure-url=/",
        "app.social-login.google.client-id=test-google-client",
        "app.social-login.google.client-secret=test-google-secret",
        "app.social-login.github.client-id=test-github-client",
        "app.social-login.github.client-secret=test-github-secret"
})
@AutoConfigureMockMvc
class SocialLoginIntegrationTests {
    private static final String REFRESH_COOKIE = "vote_refresh";

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

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired UserIdentityRepository identityRepository;
    @Autowired SocialLoginService socialLoginService;
    @Autowired SocialAuthenticationSuccessHandler successHandler;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void githubPrivateEmailCreatesOneStableLocalUser() {
        SocialProfile profile = new SocialProfile(
                SocialProvider.GITHUB, "github-private-101", null, false, "Private Octocat");

        AppUser first = socialLoginService.complete(
                profile, SocialAuthContext.authenticate(SocialIntent.AUTHENTICATE));
        AppUser second = socialLoginService.complete(
                profile, SocialAuthContext.authenticate(SocialIntent.AUTHENTICATE));

        assertEquals(first.getId(), second.getId());
        assertNull(first.getEmail());
        assertNull(first.getPasswordHash());
        assertEquals(1, identityRepository.findAllByUserId(first.getId()).size());
    }

    @Test
    void verifiedGoogleEmailRequiresExplicitAuthenticatedLinking() throws Exception {
        AppUser existing = userRepository.saveAndFlush(AppUser.create(
                "existing-google@example.com",
                "Existing Voter",
                passwordEncoder.encode("runtime-password")));
        SocialProfile profile = new SocialProfile(
                SocialProvider.GOOGLE,
                "google-existing-202",
                "existing-google@example.com",
                true,
                "Existing Google Voter");

        SocialLoginException collision = assertThrows(SocialLoginException.class, () ->
                socialLoginService.complete(profile, SocialAuthContext.authenticate(SocialIntent.AUTHENTICATE)));
        assertEquals("account_link_required", collision.code());
        assertTrue(identityRepository.findAllByUserId(existing.getId()).isEmpty());

        AppUser linked = socialLoginService.complete(profile, SocialAuthContext.link(existing.getId()));
        assertEquals(existing.getId(), linked.getId());
        assertEquals(SocialProvider.GOOGLE,
                identityRepository.findAllByUserId(existing.getId()).getFirst().getProvider());

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"existing-google@example.com","password":"runtime-password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.id").value(existing.getId().toString()))
                .andExpect(jsonPath("$.profile.linkedProviders[0]").value("GOOGLE"))
                .andReturn();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(extractRefreshCookie(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.id").value(existing.getId().toString()))
                .andExpect(jsonPath("$.profile.linkedProviders[0]").value("GOOGLE"));
    }

    @Test
    void providerSubjectCannotBeLinkedToAnotherLocalUser() {
        AppUser first = userRepository.saveAndFlush(AppUser.create(
                "first-link@example.com", "First Link", passwordEncoder.encode("runtime-password")));
        AppUser second = userRepository.saveAndFlush(AppUser.create(
                "second-link@example.com", "Second Link", passwordEncoder.encode("runtime-password")));
        SocialProfile profile = new SocialProfile(
                SocialProvider.GITHUB, "github-link-303", null, false, "Link Owner");

        socialLoginService.complete(profile, SocialAuthContext.link(first.getId()));
        SocialLoginException conflict = assertThrows(SocialLoginException.class, () ->
                socialLoginService.complete(profile, SocialAuthContext.link(second.getId())));
        assertEquals("identity_already_linked", conflict.code());
    }

    @Test
    void socialStartStoresIntentAndGoogleAuthorizationUsesStateAndNonce() throws Exception {
        MvcResult start = mockMvc.perform(post("/api/v1/auth/social/google/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intent\":\"create-ballot\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizationUrl").value("http://localhost/oauth2/authorization/google"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) start.getRequest().getSession(false);
        SocialAuthContext context = (SocialAuthContext) session.getAttribute(SocialAuthContext.SESSION_ATTRIBUTE);
        assertEquals(SocialIntent.CREATE_BALLOT, context.intent());

        MvcResult redirect = mockMvc.perform(get("/oauth2/authorization/google").session(session))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String redirectedUrl = redirect.getResponse().getRedirectedUrl();
        assertTrue(redirectedUrl.startsWith("https://accounts.google.com/o/oauth2/v2/auth?"));
        URI location = URI.create(redirectedUrl);
        Map<String, String> query = query(location.getRawQuery());
        assertFalse(query.get("state").isBlank());
        assertFalse(query.get("nonce").isBlank());
    }

    @Test
    void mismatchedStateReturnsSafeFailureRedirectWithoutTokenExchange() throws Exception {
        MvcResult start = mockMvc.perform(post("/api/v1/auth/social/google/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intent\":\"authenticate\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) start.getRequest().getSession(false);

        mockMvc.perform(get("/login/oauth2/code/google")
                        .session(session)
                        .param("code", "not-exchanged")
                        .param("state", "mismatched-state"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/?social=error&code=*&intent=authenticate"));
    }

    @Test
    void linkStartRequiresJwtAndStoresAuthenticatedUserId() throws Exception {
        mockMvc.perform(post("/api/v1/auth/social/github/link/start"))
                .andExpect(status().isUnauthorized());

        UUID userId = UUID.randomUUID();
        MvcResult linked = mockMvc.perform(post("/api/v1/auth/social/github/link/start")
                        .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizationUrl").value("http://localhost/oauth2/authorization/github"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) linked.getRequest().getSession(false);
        SocialAuthContext context = (SocialAuthContext) session.getAttribute(SocialAuthContext.SESSION_ATTRIBUTE);
        assertEquals(SocialIntent.LINK_ACCOUNT, context.intent());
        assertEquals(userId, context.linkUserId());
    }

    @Test
    void githubCallbackIssuesRefreshCookieAndDoesNotExposeProviderToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                SocialAuthContext.SESSION_ATTRIBUTE,
                SocialAuthContext.authenticate(SocialIntent.CREATE_BALLOT));
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        DefaultOAuth2User principal = new DefaultOAuth2User(
                Set.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("id", 404L, "login", "runtime-octocat", "name", "Runtime Octocat"),
                "id");
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
                principal, principal.getAuthorities(), "github");

        successHandler.onAuthenticationSuccess(request, response, authentication);

        assertEquals("/?social=success&provider=github&intent=create-ballot", response.getRedirectedUrl());
        String refreshCookie = response.getHeader("Set-Cookie");
        assertTrue(refreshCookie.contains("vote_refresh="));
        assertTrue(refreshCookie.contains("HttpOnly"));
        assertFalse(refreshCookie.contains("runtime-octocat"));
        AppUser user = identityRepository
                .findByProviderAndProviderSubject(SocialProvider.GITHUB, "404")
                .orElseThrow()
                .getUser();
        assertNull(user.getEmail());
    }

    private Cookie extractRefreshCookie(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertTrue(setCookie != null && setCookie.contains(REFRESH_COOKIE + "="));
        String prefix = REFRESH_COOKIE + "=";
        int start = setCookie.indexOf(prefix);
        int end = setCookie.indexOf(';', start);
        String value = setCookie.substring(start + prefix.length(), end < 0 ? setCookie.length() : end);
        return new Cookie(REFRESH_COOKIE, value);
    }

    private Map<String, String> query(String rawQuery) {
        return java.util.Arrays.stream(rawQuery.split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(java.util.stream.Collectors.toMap(
                        pair -> pair[0],
                        pair -> pair.length == 2 ? pair[1] : ""));
    }
}
