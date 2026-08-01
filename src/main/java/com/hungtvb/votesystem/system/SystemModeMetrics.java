package com.hungtvb.votesystem.system;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
public class SystemModeMetrics {
    private static final Set<String> SAFE_METHODS = Set.of(
            "GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
    );

    private final MeterRegistry meterRegistry;

    public SystemModeMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordRejection(String code,
                                SystemMode mode,
                                String method,
                                String path) {
        Counter.builder("vote.system.mode.requests")
                .description("Requests rejected by the authoritative operating-mode boundary")
                .tags(
                        "result", "rejected",
                        "status", "503",
                        "code", safeCode(code),
                        "mode", mode == null ? "UNKNOWN" : mode.name(),
                        "method", safeMethod(method),
                        "route", routeFamily(path)
                )
                .register(meterRegistry)
                .increment();
    }

    private String safeCode(String code) {
        if (SystemModeEnforcementFilter.READ_ONLY_CODE.equals(code)
                || SystemModeEnforcementFilter.MAINTENANCE_CODE.equals(code)
                || SystemModeEnforcementFilter.STATUS_UNAVAILABLE_CODE.equals(code)) {
            return code;
        }
        return "UNKNOWN";
    }

    private String safeMethod(String method) {
        if (method == null) {
            return "OTHER";
        }
        String normalized = method.toUpperCase(Locale.ROOT);
        return SAFE_METHODS.contains(normalized) ? normalized : "OTHER";
    }

    static String routeFamily(String path) {
        if (path == null) {
            return "other";
        }
        if (path.equals("/api/v1/system/status")) {
            return "system-status";
        }
        if (path.startsWith("/api/v1/admin/system/status")) {
            return "admin-system-status";
        }
        if (path.startsWith("/api/v1/auth/")) {
            return "auth";
        }
        if (path.startsWith("/api/v1/posts")) {
            return "posts";
        }
        if (path.startsWith("/api/v1/users")) {
            return "users";
        }
        if (path.startsWith("/oauth2/")) {
            return "oauth";
        }
        if (path.startsWith("/actuator")) {
            return "actuator";
        }
        return "other";
    }
}
