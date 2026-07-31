package com.hungtvb.votesystem.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hungtvb.votesystem.observability.RequestLatencyLoggingFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class SystemModeEnforcementFilter extends OncePerRequestFilter {
    public static final String READ_ONLY_CODE = "SYSTEM_READ_ONLY";
    public static final String MAINTENANCE_CODE = "SYSTEM_MAINTENANCE";

    private static final Logger LOGGER = LoggerFactory.getLogger(SystemModeEnforcementFilter.class);

    private final SystemStatusService systemStatusService;
    private final ObjectMapper objectMapper;
    private final SystemModeRequestPolicy policy = new SystemModeRequestPolicy();

    public SystemModeEnforcementFilter(SystemStatusService systemStatusService,
                                       ObjectMapper objectMapper) {
        this.systemStatusService = systemStatusService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        SystemStatusSnapshot status;
        try {
            status = systemStatusService.currentStatusSnapshot();
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "system_mode_lookup_failed request_id={} error_type={}",
                    RequestLatencyLoggingFilter.requestId(request),
                    exception.getClass().getSimpleName()
            );
            filterChain.doFilter(request, response);
            return;
        }

        SystemModeRequestPolicy.Decision decision = policy.evaluate(
                status.mode(),
                request.getMethod(),
                request.getRequestURI()
        );
        if (decision == SystemModeRequestPolicy.Decision.ALLOW) {
            filterChain.doFilter(request, response);
            return;
        }

        Rejection rejection = decision == SystemModeRequestPolicy.Decision.REJECT_READ_ONLY
                ? new Rejection(READ_ONLY_CODE, "System is read-only", "This operation is unavailable while the system is read-only")
                : new Rejection(MAINTENANCE_CODE, "Service under maintenance", "The service is temporarily unavailable for maintenance");
        writeProblem(request, response, status, rejection);
    }

    private void writeProblem(HttpServletRequest request,
                              HttpServletResponse response,
                              SystemStatusSnapshot status,
                              Rejection rejection) throws IOException {
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        retryAfterSeconds(status.estimatedEndAt(), Instant.now())
                .ifPresent(seconds -> response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(seconds)));

        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", rejection.title());
        problem.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        problem.put("detail", rejection.detail());
        problem.put("instance", request.getRequestURI());
        problem.put("timestamp", Instant.now());
        problem.put("code", rejection.code());
        problem.put("mode", status.mode().name());
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    static java.util.OptionalLong retryAfterSeconds(Instant estimatedEndAt, Instant now) {
        if (estimatedEndAt == null || !estimatedEndAt.isAfter(now)) {
            return java.util.OptionalLong.empty();
        }
        long millis = Duration.between(now, estimatedEndAt).toMillis();
        long seconds = Math.max(1L, Math.floorDiv(millis + 999L, 1000L));
        return java.util.OptionalLong.of(seconds);
    }

    private record Rejection(String code, String title, String detail) {
    }
}
