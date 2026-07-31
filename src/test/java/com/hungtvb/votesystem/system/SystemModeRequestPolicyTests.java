package com.hungtvb.votesystem.system;

import org.junit.jupiter.api.Test;

import static com.hungtvb.votesystem.system.SystemModeRequestPolicy.Decision.ALLOW;
import static com.hungtvb.votesystem.system.SystemModeRequestPolicy.Decision.REJECT_MAINTENANCE;
import static com.hungtvb.votesystem.system.SystemModeRequestPolicy.Decision.REJECT_READ_ONLY;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SystemModeRequestPolicyTests {
    private final SystemModeRequestPolicy policy = new SystemModeRequestPolicy();

    @Test
    void normalAllowsSupportedRequests() {
        assertEquals(ALLOW, policy.evaluate(SystemMode.NORMAL, "GET", "/api/v1/posts"));
        assertEquals(ALLOW, policy.evaluate(SystemMode.NORMAL, "POST", "/api/v1/posts"));
        assertEquals(ALLOW, policy.evaluate(SystemMode.NORMAL, "POST", "/api/v1/auth/login"));
    }

    @Test
    void readOnlyAllowsReadsAndBlocksBusinessMutations() {
        assertEquals(ALLOW, policy.evaluate(SystemMode.READ_ONLY, "GET", "/api/v1/posts"));
        assertEquals(ALLOW, policy.evaluate(SystemMode.READ_ONLY, "HEAD", "/api/v1/posts/ballot-id"));
        assertEquals(REJECT_READ_ONLY, policy.evaluate(SystemMode.READ_ONLY, "POST", "/api/v1/posts"));
        assertEquals(REJECT_READ_ONLY, policy.evaluate(SystemMode.READ_ONLY, "PUT", "/api/v1/posts/ballot-id/vote"));
        assertEquals(REJECT_READ_ONLY, policy.evaluate(SystemMode.READ_ONLY, "POST", "/api/v1/auth/register"));
        assertEquals(ALLOW, policy.evaluate(SystemMode.READ_ONLY, "POST", "/api/v1/auth/login"));
    }

    @Test
    void maintenanceBlocksPublicReadsAndLogin() {
        assertEquals(REJECT_MAINTENANCE, policy.evaluate(SystemMode.MAINTENANCE, "GET", "/api/v1/posts"));
        assertEquals(REJECT_MAINTENANCE, policy.evaluate(SystemMode.MAINTENANCE, "GET", "/api/v1/posts/ballot-id"));
        assertEquals(REJECT_MAINTENANCE, policy.evaluate(SystemMode.MAINTENANCE, "POST", "/api/v1/auth/login"));
        assertEquals(REJECT_MAINTENANCE, policy.evaluate(SystemMode.MAINTENANCE, "GET", "/oauth2/authorization/google"));
    }

    @Test
    void recoveryEndpointsRemainAvailableInEveryRestrictedMode() {
        for (SystemMode mode : new SystemMode[]{SystemMode.READ_ONLY, SystemMode.MAINTENANCE}) {
            assertEquals(ALLOW, policy.evaluate(mode, "GET", "/api/v1/system/status"));
            assertEquals(ALLOW, policy.evaluate(mode, "HEAD", "/api/v1/system/status"));
            assertEquals(ALLOW, policy.evaluate(mode, "GET", "/actuator/health"));
            assertEquals(ALLOW, policy.evaluate(mode, "GET", "/actuator/health/readiness"));
            assertEquals(ALLOW, policy.evaluate(mode, "GET", "/api/v1/admin/system/status"));
            assertEquals(ALLOW, policy.evaluate(mode, "PUT", "/api/v1/admin/system/status"));
            assertEquals(ALLOW, policy.evaluate(mode, "POST", "/api/v1/auth/refresh"));
            assertEquals(ALLOW, policy.evaluate(mode, "POST", "/api/v1/auth/logout"));
            assertEquals(ALLOW, policy.evaluate(mode, "POST", "/api/v1/auth/logout-all"));
        }
    }

    @Test
    void corsPreflightAlwaysPassesButCannotCreateAWriteBypass() {
        assertEquals(ALLOW, policy.evaluate(SystemMode.MAINTENANCE, "OPTIONS", "/api/v1/posts"));
        assertEquals(REJECT_MAINTENANCE, policy.evaluate(SystemMode.MAINTENANCE, "POST", "/api/v1/posts?bypass=true"));
        assertEquals(REJECT_READ_ONLY, policy.evaluate(SystemMode.READ_ONLY, "POST", "/api/v1/admin/users/id/restore"));
    }
}
