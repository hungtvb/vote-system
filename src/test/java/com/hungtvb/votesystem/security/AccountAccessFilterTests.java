package com.hungtvb.votesystem.security;

import com.hungtvb.votesystem.user.AccountAccessPolicy;
import com.hungtvb.votesystem.user.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AccountAccessFilterTests {
    private final AccountAccessPolicy accountAccessPolicy = mock(AccountAccessPolicy.class);
    private final AccountAccessFilter filter = new AccountAccessFilter(accountAccessPolicy);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allowsTokenThatMatchesCurrentRoleAndSecurityVersion() throws Exception {
        UUID userId = UUID.randomUUID();
        AppUser user = AppUser.create("user@example.com", "hash");
        when(accountAccessPolicy.requireActive(userId)).thenReturn(user);
        authenticate(userId, "USER", 0L);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsRoleClaimThatDoesNotMatchDatabaseRole() throws Exception {
        UUID userId = UUID.randomUUID();
        AppUser user = AppUser.create("user@example.com", "hash");
        when(accountAccessPolicy.requireActive(userId)).thenReturn(user);
        authenticate(userId, "ADMIN", 0L);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    void rejectsTokenIssuedBeforeSecurityVersionRotation() throws Exception {
        UUID userId = UUID.randomUUID();
        AppUser user = AppUser.create("user@example.com", "hash");
        user.revokeAccessTokens();
        when(accountAccessPolicy.requireActive(userId)).thenReturn(user);
        authenticate(userId, "USER", 0L);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    private void authenticate(UUID userId, String role, long securityVersion) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim(TokenService.ROLES_CLAIM, List.of(role))
                .claim(TokenService.SECURITY_VERSION_CLAIM, securityVersion)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        ));
    }
}
