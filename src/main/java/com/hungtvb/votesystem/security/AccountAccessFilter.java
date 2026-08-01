package com.hungtvb.votesystem.security;

import com.hungtvb.votesystem.common.error.UnauthorizedException;
import com.hungtvb.votesystem.user.AccountAccessPolicy;
import com.hungtvb.votesystem.user.AppUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class AccountAccessFilter extends OncePerRequestFilter {
    private static final String UNAVAILABLE_MESSAGE = "User account is unavailable";

    private final AccountAccessPolicy accountAccessPolicy;
    private final BearerTokenAuthenticationEntryPoint entryPoint = new BearerTokenAuthenticationEntryPoint();

    public AccountAccessFilter(AccountAccessPolicy accountAccessPolicy) {
        this.accountAccessPolicy = accountAccessPolicy;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            AppUser user = accountAccessPolicy.requireActive(
                    UUID.fromString(jwtAuthentication.getToken().getSubject())
            );
            requireCurrentTokenState(jwtAuthentication, user);
            filterChain.doFilter(request, response);
        } catch (IllegalArgumentException | UnauthorizedException exception) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(request, response, new BadCredentialsException(UNAVAILABLE_MESSAGE));
        }
    }

    private void requireCurrentTokenState(JwtAuthenticationToken authentication, AppUser user) {
        Object rawVersion = authentication.getToken().getClaims().get(TokenService.SECURITY_VERSION_CLAIM);
        long tokenVersion = rawVersion == null ? 0L : parseVersion(rawVersion);
        List<String> tokenRoles = authentication.getToken().getClaimAsStringList(TokenService.ROLES_CLAIM);

        if (tokenVersion != user.getSecurityVersion()
                || tokenRoles == null
                || tokenRoles.size() != 1
                || !user.getRole().name().equals(tokenRoles.getFirst())) {
            throw new UnauthorizedException(UNAVAILABLE_MESSAGE);
        }
    }

    private long parseVersion(Object rawVersion) {
        if (rawVersion instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(rawVersion.toString());
    }
}
