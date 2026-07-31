package com.hungtvb.votesystem.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hungtvb.votesystem.admin.AdminBootstrapService;
import com.hungtvb.votesystem.system.dto.UpdateSystemStatusRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = "app.rate-limit.enabled=false")
@AutoConfigureMockMvc
class SystemStatusIntegrationTests {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureInfrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.url", () -> "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired AdminBootstrapService adminBootstrapService;
    @Autowired SystemStatusService systemStatusService;

    @BeforeEach
    void resetStatus() {
        jdbcTemplate.update("""
                update system_status
                   set mode = 'NORMAL',
                       message_vi = null,
                       message_en = null,
                       estimated_end_at = null,
                       updated_at = current_timestamp,
                       updated_by = null,
                       version = version + 1
                 where singleton_id = 1
                """);
        systemStatusService.evictCache();
    }

    @Test
    void publicStatusIsAnonymousAndDoesNotExposeActor() throws Exception {
        mockMvc.perform(get("/api/v1/system/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("NORMAL"))
                .andExpect(jsonPath("$.messageVi").doesNotExist())
                .andExpect(jsonPath("$.messageEn").doesNotExist())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedBy").doesNotExist());
    }

    @Test
    void adminBoundaryRejectsAnonymousAndUser() throws Exception {
        mockMvc.perform(get("/api/v1/admin/system/status"))
                .andExpect(status().isUnauthorized());

        AuthSession user = register("system-status-user@example.com");
        mockMvc.perform(get("/api/v1/admin/system/status")
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/admin/system/status")
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload(SystemMode.READ_ONLY, null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminUpdatePersistsInvalidatesCacheAndAppendsAudit() throws Exception {
        AuthSession admin = admin("system-status-admin@example.com");
        Instant estimatedEndAt = Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        long auditBefore = systemAuditCount();

        mockMvc.perform(get("/api/v1/system/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("NORMAL"));

        mockMvc.perform(put("/api/v1/admin/system/status")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .header("X-Request-ID", "maintenance-change-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload(SystemMode.MAINTENANCE, estimatedEndAt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("MAINTENANCE"))
                .andExpect(jsonPath("$.messageVi").value("Hệ thống đang bảo trì"))
                .andExpect(jsonPath("$.messageEn").value("The system is under maintenance. Contact support@ballotbox.io.vn"))
                .andExpect(jsonPath("$.estimatedEndAt").value(estimatedEndAt.toString()))
                .andExpect(jsonPath("$.updatedBy").value(admin.userId().toString()));

        mockMvc.perform(get("/api/v1/system/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("MAINTENANCE"))
                .andExpect(jsonPath("$.messageVi").value("Hệ thống đang bảo trì"));

        assertEquals("MAINTENANCE", jdbcTemplate.queryForObject(
                "select mode from system_status where singleton_id = 1", String.class));
        assertEquals(auditBefore + 1, systemAuditCount());
        assertEquals("maintenance-change-001", jdbcTemplate.queryForObject("""
                select metadata ->> 'request_id'
                  from admin_audit_logs
                 where action = 'SYSTEM_MODE_CHANGED'
                 order by created_at desc, id desc
                 limit 1
                """, String.class));
        assertEquals("The system is under maintenance. Contact support@ballotbox.io.vn",
                jdbcTemplate.queryForObject("""
                        select metadata ->> 'message_en'
                          from admin_audit_logs
                         where action = 'SYSTEM_MODE_CHANGED'
                         order by created_at desc, id desc
                         limit 1
                        """, String.class));
    }

    @Test
    void persistedStatusReloadsAfterLocalCacheEviction() throws Exception {
        AuthSession admin = admin("system-status-reload-admin@example.com");
        mockMvc.perform(put("/api/v1/admin/system/status")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload(SystemMode.READ_ONLY, null)))
                .andExpect(status().isOk());

        systemStatusService.evictCache();

        mockMvc.perform(get("/api/v1/system/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("READ_ONLY"));
    }

    @Test
    void invalidPastTimestampRollsBackStateAndAudit() throws Exception {
        AuthSession admin = admin("system-status-invalid-admin@example.com");
        long auditBefore = systemAuditCount();
        Instant past = Instant.now().minus(1, ChronoUnit.MINUTES);

        mockMvc.perform(put("/api/v1/admin/system/status")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload(SystemMode.MAINTENANCE, past)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("estimatedEndAt must be in the future"));

        assertEquals("NORMAL", jdbcTemplate.queryForObject(
                "select mode from system_status where singleton_id = 1", String.class));
        assertEquals(auditBefore, systemAuditCount());
    }

    @Test
    void invalidModeAndBlankReasonReturnValidationErrors() throws Exception {
        AuthSession admin = admin("system-status-validation-admin@example.com");

        mockMvc.perform(put("/api/v1/admin/system/status")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"PAUSED\",\"reason\":\"Invalid mode\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/admin/system/status")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"READ_ONLY\",\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void normalModeClearsMessagesAndEstimatedEnd() throws Exception {
        AuthSession admin = admin("system-status-normal-admin@example.com");
        mockMvc.perform(put("/api/v1/admin/system/status")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload(SystemMode.MAINTENANCE,
                                Instant.now().plus(1, ChronoUnit.HOURS))))
                .andExpect(status().isOk());

        UpdateSystemStatusRequest normal = new UpdateSystemStatusRequest(
                SystemMode.NORMAL,
                "This copy must be cleared",
                "This copy must be cleared",
                Instant.now().plus(2, ChronoUnit.HOURS),
                "Return to normal operation"
        );
        mockMvc.perform(put("/api/v1/admin/system/status")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(normal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("NORMAL"))
                .andExpect(jsonPath("$.messageVi").doesNotExist())
                .andExpect(jsonPath("$.messageEn").doesNotExist())
                .andExpect(jsonPath("$.estimatedEndAt").doesNotExist());
    }

    @Test
    void identicalUpdateIsRejectedWithoutDuplicateAudit() throws Exception {
        AuthSession admin = admin("system-status-noop-admin@example.com");
        String payload = updatePayload(SystemMode.READ_ONLY, null);
        mockMvc.perform(put("/api/v1/admin/system/status")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
        long auditBefore = systemAuditCount();

        mockMvc.perform(put("/api/v1/admin/system/status")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict());

        assertEquals(auditBefore, systemAuditCount());
    }

    private AuthSession admin(String email) throws Exception {
        AuthSession registered = register(email);
        assertTrue(adminBootstrapService.promoteExistingUser(email));
        return new AuthSession(login(email), registered.userId());
    }

    private AuthSession register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"strong-password\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode payload = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID userId = UUID.fromString(payload.get("profile").get("id").asText());
        assertNotNull(userId);
        return new AuthSession(payload.get("accessToken").asText(), userId);
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"strong-password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String updatePayload(SystemMode mode, Instant estimatedEndAt) throws Exception {
        return objectMapper.writeValueAsString(new UpdateSystemStatusRequest(
                mode,
                "Hệ thống đang bảo trì",
                "The system is under maintenance. Contact support@ballotbox.io.vn",
                estimatedEndAt,
                "Planned maintenance operation"
        ));
    }

    private long systemAuditCount() {
        return jdbcTemplate.queryForObject(
                "select count(*) from admin_audit_logs where action = 'SYSTEM_MODE_CHANGED'",
                Long.class
        );
    }

    private record AuthSession(String accessToken, UUID userId) {
    }
}
