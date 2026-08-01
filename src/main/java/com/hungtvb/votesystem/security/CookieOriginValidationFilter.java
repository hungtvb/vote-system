package com.hungtvb.votesystem.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hungtvb.votesystem.common.config.CorsProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class CookieOriginValidationFilter extends OncePerRequestFilter {
    private static final Set<String> COOKIE_SESSION_PATHS = Set.of(
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout"
    );

    private final Set<String> allowedOrigins;
    private final ObjectMapper objectMapper;

    public CookieOriginValidationFilter(CorsProperties corsProperties, ObjectMapper objectMapper) {
        this.allowedOrigins = Set.copyOf(corsProperties.allowedOrigins());
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!isProtectedRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String origin = request.getHeader(HttpHeaders.ORIGIN);
        String fetchSite = request.getHeader("Sec-Fetch-Site");
        if (origin != null && !origin.isBlank()) {
            if (!allowedOrigins.contains(origin) && !isSameOrigin(request, origin)) {
                reject(request, response);
                return;
            }
        } else if (fetchSite != null
                && !"same-origin".equalsIgnoreCase(fetchSite)
                && !"same-site".equalsIgnoreCase(fetchSite)
                && !"none".equalsIgnoreCase(fetchSite)) {
            reject(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isProtectedRequest(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return COOKIE_SESSION_PATHS.contains(path)
                || path.matches("/api/v1/auth/social/(google|github)/(link/)?start");
    }

    private boolean isSameOrigin(HttpServletRequest request, String origin) {
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        String scheme = forwardedProto == null || forwardedProto.isBlank()
                ? request.getScheme()
                : forwardedProto.split(",", 2)[0].trim();
        String forwardedHost = request.getHeader("X-Forwarded-Host");
        String authority = forwardedHost == null || forwardedHost.isBlank()
                ? request.getHeader(HttpHeaders.HOST)
                : forwardedHost.split(",", 2)[0].trim();
        return authority != null && origin.equalsIgnoreCase(scheme + "://" + authority);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", "Forbidden");
        problem.put("status", HttpStatus.FORBIDDEN.value());
        problem.put("detail", "The browser origin is not allowed for this session operation");
        problem.put("instance", request.getRequestURI());
        problem.put("timestamp", Instant.now());
        problem.put("code", "SESSION_ORIGIN_REJECTED");
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
