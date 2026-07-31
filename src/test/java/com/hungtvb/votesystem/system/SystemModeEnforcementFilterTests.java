package com.hungtvb.votesystem.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemModeEnforcementFilterTests {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final SystemStatusService statusService = mock(SystemStatusService.class);
    private final SystemModeEnforcementFilter filter = new SystemModeEnforcementFilter(statusService, objectMapper);

    @Test
    void readOnlyRejectsMutationWithStableProblemAndRetryAfter() throws Exception {
        Instant now = Instant.now();
        when(statusService.currentStatusSnapshot()).thenReturn(snapshot(
                SystemMode.READ_ONLY,
                now.plusSeconds(90)
        ));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/posts");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chained = new AtomicBoolean();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> chained.set(true));

        assertFalse(chained.get());
        assertEquals(503, response.getStatus());
        assertEquals("application/problem+json", response.getContentType());
        long retryAfter = Long.parseLong(response.getHeader("Retry-After"));
        assertTrue(retryAfter >= 89 && retryAfter <= 90);
        JsonNode problem = objectMapper.readTree(response.getContentAsByteArray());
        assertEquals(SystemModeEnforcementFilter.READ_ONLY_CODE, problem.get("code").asText());
        assertEquals("READ_ONLY", problem.get("mode").asText());
        assertEquals("/api/v1/posts", problem.get("instance").asText());
    }

    @Test
    void maintenanceRejectsPublicReadWithoutInventingRetryAfter() throws Exception {
        when(statusService.currentStatusSnapshot()).thenReturn(snapshot(SystemMode.MAINTENANCE, null));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/posts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            throw new AssertionError("Maintenance request must not reach the application");
        });

        assertEquals(503, response.getStatus());
        assertNull(response.getHeader("Retry-After"));
        JsonNode problem = objectMapper.readTree(response.getContentAsByteArray());
        assertEquals(SystemModeEnforcementFilter.MAINTENANCE_CODE, problem.get("code").asText());
        assertEquals("MAINTENANCE", problem.get("mode").asText());
    }

    @Test
    void maintenancePreservesRefreshAndAdminRecoveryPaths() throws Exception {
        when(statusService.currentStatusSnapshot()).thenReturn(snapshot(SystemMode.MAINTENANCE, null));
        for (MockHttpServletRequest request : new MockHttpServletRequest[]{
                new MockHttpServletRequest("POST", "/api/v1/auth/refresh"),
                new MockHttpServletRequest("POST", "/api/v1/auth/logout"),
                new MockHttpServletRequest("POST", "/api/v1/auth/logout-all"),
                new MockHttpServletRequest("PUT", "/api/v1/admin/system/status"),
                new MockHttpServletRequest("GET", "/api/v1/system/status"),
                new MockHttpServletRequest("GET", "/actuator/health"),
                new MockHttpServletRequest("OPTIONS", "/api/v1/posts")
        }) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicBoolean chained = new AtomicBoolean();
            filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> chained.set(true));
            assertTrue(chained.get(), () -> request.getMethod() + " " + request.getRequestURI());
        }
    }

    @Test
    void statusLookupFailureFailsOpenForRecovery() throws Exception {
        when(statusService.currentStatusSnapshot()).thenThrow(new IllegalStateException("status unavailable"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/posts");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chained = new AtomicBoolean();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> chained.set(true));

        assertTrue(chained.get());
        assertEquals(200, response.getStatus());
    }

    @Test
    void retryAfterRoundsUpAndIgnoresPastValues() {
        Instant now = Instant.parse("2026-07-31T00:00:00Z");
        assertEquals(2, SystemModeEnforcementFilter.retryAfterSeconds(
                now.plusMillis(1001), now).orElseThrow());
        assertTrue(SystemModeEnforcementFilter.retryAfterSeconds(now, now).isEmpty());
        assertTrue(SystemModeEnforcementFilter.retryAfterSeconds(now.minusSeconds(1), now).isEmpty());
        assertTrue(SystemModeEnforcementFilter.retryAfterSeconds(null, now).isEmpty());
    }

    private SystemStatusSnapshot snapshot(SystemMode mode, Instant estimatedEndAt) {
        return new SystemStatusSnapshot(
                mode,
                mode == SystemMode.NORMAL ? null : "Thông báo hệ thống",
                mode == SystemMode.NORMAL ? null : "System notice",
                estimatedEndAt,
                Instant.now(),
                UUID.randomUUID()
        );
    }
}
