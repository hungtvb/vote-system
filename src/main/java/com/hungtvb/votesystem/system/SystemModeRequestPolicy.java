package com.hungtvb.votesystem.system;

import org.springframework.http.HttpMethod;

import java.util.Set;

final class SystemModeRequestPolicy {
    private static final String PUBLIC_STATUS = "/api/v1/system/status";
    private static final String ADMIN_STATUS = "/api/v1/admin/system/status";
    private static final Set<String> SESSION_RECOVERY_PATHS = Set.of(
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/logout-all"
    );

    Decision evaluate(SystemMode mode, String method, String path) {
        if (isCorsPreflight(method) || isRecoveryEndpoint(method, path)) {
            return Decision.ALLOW;
        }
        if (mode == SystemMode.NORMAL) {
            return Decision.ALLOW;
        }
        if (mode == SystemMode.READ_ONLY && isSafeRead(method)) {
            return Decision.ALLOW;
        }
        return mode == SystemMode.READ_ONLY ? Decision.REJECT_READ_ONLY : Decision.REJECT_MAINTENANCE;
    }

    private boolean isRecoveryEndpoint(String method, String path) {
        if (path == null) {
            return false;
        }
        if ((HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method))
                && (PUBLIC_STATUS.equals(path) || isHealthPath(path))) {
            return true;
        }
        if ((HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method) || HttpMethod.PUT.matches(method))
                && ADMIN_STATUS.equals(path)) {
            return true;
        }
        return HttpMethod.POST.matches(method) && SESSION_RECOVERY_PATHS.contains(path);
    }

    private boolean isHealthPath(String path) {
        return "/actuator/health".equals(path) || path.startsWith("/actuator/health/");
    }

    private boolean isSafeRead(String method) {
        return HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method);
    }

    private boolean isCorsPreflight(String method) {
        return HttpMethod.OPTIONS.matches(method);
    }

    enum Decision {
        ALLOW,
        REJECT_READ_ONLY,
        REJECT_MAINTENANCE
    }
}
