package com.hungtvb.votesystem.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private final RedisSlidingWindowRateLimiter rateLimiter;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RedisSlidingWindowRateLimiter rateLimiter,
                           RateLimitProperties properties,
                           ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Rule rule = resolveRule(request);
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String subject = rule.userScoped() ? authenticatedSubject(request) : clientIp(request);
        RateLimitDecision decision = rateLimiter.check(rule.name(), subject, rule.policy());
        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", "Too Many Requests");
        problem.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        problem.put("detail", "Rate limit exceeded. Retry after " + decision.retryAfterSeconds() + " seconds");
        problem.put("instance", request.getRequestURI());
        problem.put("timestamp", Instant.now());
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    private Rule resolveRule(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if ("POST".equals(method) && "/api/v1/auth/login".equals(path)) {
            return new Rule("login", properties.login(), false);
        }
        if ("POST".equals(method) && "/api/v1/auth/register".equals(path)) {
            return new Rule("register", properties.register(), false);
        }
        if ("POST".equals(method) && "/api/v1/auth/refresh".equals(path)) {
            return new Rule("refresh", properties.refresh(), false);
        }
        if ("POST".equals(method) && path.matches("/api/v1/auth/social/(google|github)/start")) {
            return new Rule("social-start", properties.socialStart(), false);
        }
        if ("POST".equals(method) && path.matches("/api/v1/auth/social/(google|github)/link/start")) {
            return new Rule("social-link", properties.socialStart(), true);
        }
        if ("POST".equals(method) && "/api/v1/posts".equals(path)) {
            return new Rule("create-post", properties.createPost(), true);
        }
        if (("PUT".equals(method) || "DELETE".equals(method))
                && path.matches("/api/v1/posts/[0-9a-fA-F-]+/vote")) {
            return new Rule("vote", properties.vote(), true);
        }
        return null;
    }

    private String authenticatedSubject(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication.getName() != null) {
            return authentication.getName();
        }
        return "ip:" + clientIp(request);
    }

    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    private record Rule(String name, RateLimitProperties.Policy policy, boolean userScoped) {
    }
}
