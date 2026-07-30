package com.hungtvb.votesystem.security;

import com.hungtvb.votesystem.common.error.UnauthorizedException;
import com.hungtvb.votesystem.user.AccountAccessPolicy;
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
            accountAccessPolicy.requireActive(UUID.fromString(jwtAuthentication.getToken().getSubject()));
            filterChain.doFilter(request, response);
        } catch (IllegalArgumentException | UnauthorizedException exception) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(request, response, new BadCredentialsException(UNAVAILABLE_MESSAGE));
        }
    }
}
