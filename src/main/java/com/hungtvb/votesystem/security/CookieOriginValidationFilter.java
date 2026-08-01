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
        } else if (!isAllowedWithoutOrigin(fetchSite)) {
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

    private boolean isAllowedWithoutOrigin(String fetchSite) {
        if (fetchSite == null || fetchSite.isBlank()) {
            return true;
        }
        return "same-origin".equalsIgnoreCase(fetchSite)
                || "none".equalsIgnoreCase(fetchSite);
    }

    private boolean isSameOrigin(HttpServletRequest request, String origin) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        if (scheme == null || host == null || scheme.isBlank() || host.isBlank()) {
            return false;
        }

        boolean defaultPort = ("https".equalsIgnoreCase(scheme) && port == 443)
                || ("http".equalsIgnoreCase(scheme) && port == 80);
        String normalizedHost = host.contains(":") && !host.startsWith("[")
                ? "[" + host + "]"
                : host;
        String requestOrigin = scheme + "://" + normalizedHost + (defaultPort ? "" : ":" + port);
        return origin.equalsIgnoreCase(requestOrigin);
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
