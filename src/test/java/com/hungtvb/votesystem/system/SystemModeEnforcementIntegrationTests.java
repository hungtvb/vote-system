package com.hungtvb.votesystem.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hungtvb.votesystem.admin.AdminBootstrapService;
import com.hungtvb.votesystem.system.dto.UpdateSystemStatusRequest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = "app.rate-limit.enabled=false")
@AutoConfigureMockMvc
class SystemModeEnforcementIntegrationTests {

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
        setModeDirectly(SystemMode.NORMAL, null);
    }

    @Test
    void readOnlyKeepsReadsAndLoginButBlocksRegistrationAndBusinessWrites() throws Exception {
        AuthSession user = register("read-only-existing@example.com");
        setModeDirectly(SystemMode.READ_ONLY, Instant.now().plus(1, ChronoUnit.HOURS));

        mockMvc.perform(get("/api/v1/posts"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("read-only-existing@example.com")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("read-only-new@example.com")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(SystemModeEnforcementFilter.READ_ONLY_CODE));

        mockMvc.perform(post("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(SystemModeEnforcementFilter.READ_ONLY_CODE));

        mockMvc.perform(put("/api/v1/posts/" + UUID.randomUUID() + "/vote")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":1}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(SystemModeEnforcementFilter.READ_ONLY_CODE));
    }

    @Test
    void maintenanceBlocksPublicApplicationButPreservesStatusHealthAndCors() throws Exception {
        register("maintenance-login@example.com");
        setModeDirectly(SystemMode.MAINTENANCE, Instant.now().plus(30, ChronoUnit.MINUTES));

        mockMvc.perform(get("/api/v1/posts"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(SystemModeEnforcementFilter.MAINTENANCE_CODE))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("maintenance-login@example.com")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(SystemModeEnforcementFilter.MAINTENANCE_CODE));

        mockMvc.perform(get("/api/v1/system/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("MAINTENANCE"));

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        mockMvc.perform(options("/api/v1/posts")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
    }

    @Test
    void normalUserCannotExploitExemptAdminRecoveryPath() throws Exception {
        AuthSession user = register("maintenance-user@example.com");
        setModeDirectly(SystemMode.MAINTENANCE, Instant.now().plus(30, ChronoUnit.MINUTES));

        mockMvc.perform(put("/api/v1/admin/system/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload(SystemMode.NORMAL, null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorCanRefreshAndRestoreNormalOperationAfterMaintenance() throws Exception {
        AuthSession admin = admin("maintenance-recovery-admin@example.com");
        Instant estimatedEnd = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);

        mockMvc.perform(put("/api/v1/admin/system/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload(SystemMode.MAINTENANCE, estimatedEnd)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("MAINTENANCE"));

        mockMvc.perform(get("/api/v1/posts"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(SystemModeEnforcementFilter.MAINTENANCE_CODE));

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(admin.refreshCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.role").value("ADMIN"))
                .andReturn();
        AuthSession refreshedAdmin = sessionFrom(refreshed);

        mockMvc.perform(put("/api/v1/admin/system/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(refreshedAdmin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload(SystemMode.NORMAL, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("NORMAL"));

        mockMvc.perform(get("/api/v1/posts"))
                .andExpect(status().isOk());
    }

    private AuthSession admin(String email) throws Exception {
        register(email);
        assertTrue(adminBootstrapService.promoteExistingUser(email));
        return login(email);
    }

    private AuthSession register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email)))
                .andExpect(status().isCreated())
                .andReturn();
        return sessionFrom(result);
    }

    private AuthSession login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email)))
                .andExpect(status().isOk())
                .andReturn();
        return sessionFrom(result);
    }

    private AuthSession sessionFrom(MvcResult result) throws Exception {
        JsonNode payload = objectMapper.readTree(result.getResponse().getContentAsString());
        String accessToken = payload.get("accessToken").asText();
        UUID userId = UUID.fromString(payload.get("profile").get("id").asText());
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        String pair = setCookie.substring(0, setCookie.indexOf(';'));
        String[] parts = pair.split("=", 2);
        return new AuthSession(accessToken, userId, new Cookie(parts[0], parts[1]));
    }

    private void setModeDirectly(SystemMode mode, Instant estimatedEndAt) {
        jdbcTemplate.update("""
                update system_status
                   set mode = ?,
                       message_vi = ?,
                       message_en = ?,
                       estimated_end_at = ?,
                       updated_at = current_timestamp,
                       updated_by = null,
                       version = version + 1
                 where singleton_id = 1
                """,
                mode.name(),
                mode == SystemMode.NORMAL ? null : "Thông báo hệ thống",
                mode == SystemMode.NORMAL ? null : "System notice",
                estimatedEndAt == null ? null : Timestamp.from(estimatedEndAt));
        systemStatusService.evictCache();
    }

    private String updatePayload(SystemMode mode, Instant estimatedEndAt) throws Exception {
        return objectMapper.writeValueAsString(new UpdateSystemStatusRequest(
                mode,
                mode == SystemMode.NORMAL ? null : "Hệ thống đang bảo trì",
                mode == SystemMode.NORMAL ? null : "The system is under maintenance",
                estimatedEndAt,
                mode == SystemMode.NORMAL ? "Restore normal operation" : "Planned maintenance"
        ));
    }

    private String credentials(String email) {
        return "{\"email\":\"" + email + "\",\"password\":\"strong-password\"}";
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record AuthSession(String accessToken, UUID userId, Cookie refreshCookie) {
    }
}
