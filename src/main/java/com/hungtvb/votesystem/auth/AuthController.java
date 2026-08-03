package com.hungtvb.votesystem.auth;

import com.hungtvb.votesystem.auth.dto.AuthResponse;
import com.hungtvb.votesystem.auth.dto.LoginRequest;
import com.hungtvb.votesystem.auth.dto.RegisterRequest;
import com.hungtvb.votesystem.auth.metrics.AuthRestoreMetrics;
import com.hungtvb.votesystem.auth.session.RefreshSessionFailureException;
import com.hungtvb.votesystem.auth.session.SessionClientMetadataFactory;
import com.hungtvb.votesystem.observability.RequestLatencyLoggingFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final RefreshTokenCookie refreshTokenCookie;
    private final AuthRestoreMetrics metrics;
    private final SessionClientMetadataFactory sessionMetadataFactory;

    public AuthController(AuthService authService,
                          RefreshTokenCookie refreshTokenCookie,
                          AuthRestoreMetrics metrics,
                          SessionClientMetadataFactory sessionMetadataFactory) {
        this.authService = authService;
        this.refreshTokenCookie = refreshTokenCookie;
        this.metrics = metrics;
        this.sessionMetadataFactory = sessionMetadataFactory;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    AuthResponse register(@Valid @RequestBody RegisterRequest request,
                          HttpServletRequest httpRequest,
                          HttpServletResponse response) {
        return writeSession(authService.register(request, sessionMetadataFactory.password(httpRequest)), response);
    }

    @PostMapping("/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request,
                       HttpServletRequest httpRequest,
                       HttpServletResponse response) {
        return writeSession(authService.login(request, sessionMetadataFactory.password(httpRequest)), response);
    }

    @PostMapping("/refresh")
    AuthResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        String requestId = RequestLatencyLoggingFilter.requestId(request);
        AuthRestoreMetrics.RestoreSample sample = metrics.startTotal(AuthRestoreMetrics.OPERATION_REFRESH);
        String outcome = "error";

        try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", requestId)) {
            try {
                AuthResponse authResponse = writeSession(
                        authService.refresh(refreshTokenCookie.read(request)),
                        response
                );
                outcome = "success";
                return authResponse;
            } catch (RefreshSessionFailureException exception) {
                outcome = exception.reason().metricValue();
                throw exception;
            } finally {
                long durationMillis = metrics.stopTotal(sample, outcome);
                LOGGER.info(
                        "auth_restore operation=refresh outcome={} duration_ms={} request_id={}",
                        outcome,
                        durationMillis,
                        requestId
                );
            }
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(refreshTokenCookie.read(request));
        refreshTokenCookie.clear(response);
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logoutAll(@AuthenticationPrincipal Jwt jwt, HttpServletResponse response) {
        authService.logoutAll(UUID.fromString(jwt.getSubject()));
        refreshTokenCookie.clear(response);
    }

    private AuthResponse writeSession(IssuedAuthSession session, HttpServletResponse response) {
        refreshTokenCookie.write(response, session.refreshToken());
        return session.response();
    }
}
