package com.hungtvb.votesystem.admin.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hungtvb.votesystem.admin.AdminBootstrapService;
import com.hungtvb.votesystem.admin.audit.dto.AdminAuditLogResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AdminAuditLogIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired AdminBootstrapService adminBootstrapService;
    @Autowired AdminAuditLogService auditLogService;
    @Autowired AdminAuditLogController auditLogController;

    @Test
    void adminCanReadFilteredAuditLogsWhileOtherCallersAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs"))
                .andExpect(status().isUnauthorized());

        AuthSession user = register("audit-reader-user@example.com");
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isForbidden());

        String adminEmail = "audit-reader-admin@example.com";
        AuthSession admin = register(adminEmail);
        adminBootstrapService.promoteExistingUser(adminEmail);
        String adminToken = login(adminEmail);
        String targetId = UUID.randomUUID().toString();

        auditLogService.append(new AdminAuditEvent(
                admin.userId(),
                AdminAuditAction.ADMIN_HIDE_POST,
                AdminAuditTargetType.POST,
                targetId,
                "Contains prohibited content",
                Map.of("request_id", "req-hide-1")
        ));
        Thread.sleep(5);
        auditLogService.append(new AdminAuditEvent(
                admin.userId(),
                AdminAuditAction.ADMIN_RESTORE_POST,
                AdminAuditTargetType.POST,
                targetId,
                "Appeal was accepted",
                Map.of("request_id", "req-restore-1")
        ));

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("actorId", admin.userId().toString())
                        .param("action", "ADMIN_HIDE_POST")
                        .param("targetType", "POST")
                        .param("targetId", targetId)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].actorId").value(admin.userId().toString()))
                .andExpect(jsonPath("$.content[0].action").value("ADMIN_HIDE_POST"))
                .andExpect(jsonPath("$.content[0].targetType").value("POST"))
                .andExpect(jsonPath("$.content[0].targetId").value(targetId))
                .andExpect(jsonPath("$.content[0].reason").value("Contains prohibited content"))
                .andExpect(jsonPath("$.content[0].metadata.request_id").value("req-hide-1"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("actorId", admin.userId().toString())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].action").value("ADMIN_RESTORE_POST"))
                .andExpect(jsonPath("$.content[1].action").value("ADMIN_HIDE_POST"));
    }

    @Test
    void databaseTriggerRejectsAuditUpdatesAndDeletes() throws Exception {
        String adminEmail = "audit-immutable-admin@example.com";
        AuthSession admin = register(adminEmail);
        adminBootstrapService.promoteExistingUser(adminEmail);

        AdminAuditLogResponse auditLog = auditLogService.append(new AdminAuditEvent(
                admin.userId(),
                AdminAuditAction.ADMIN_REBUILD_RANKING,
                AdminAuditTargetType.RANKING,
                "ALL",
                "Rebuild requested after recovery",
                Map.of("feed", "all")
        ));

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "update admin_audit_logs set reason = ? where id = ?",
                "Mutated reason",
                auditLog.id()
        ));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "delete from admin_audit_logs where id = ?",
                auditLog.id()
        ));

        assertEquals(
                "Rebuild requested after recovery",
                jdbcTemplate.queryForObject(
                        "select reason from admin_audit_logs where id = ?",
                        String.class,
                        auditLog.id()
                )
        );
    }

    @Test
    @WithMockUser(roles = "USER")
    void methodGuardRejectsDirectUserInvocation() {
        assertThrows(AccessDeniedException.class, () -> auditLogController.list(
                null,
                null,
                null,
                null,
                0,
                20
        ));
    }

    private AuthSession register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"strong-password\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.profile.role").value("USER"))
                .andReturn();
        JsonNode payload = objectMapper.readTree(result.getResponse().getContentAsString());
        return new AuthSession(
                payload.get("accessToken").asText(),
                UUID.fromString(payload.get("profile").get("id").asText())
        );
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"strong-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.role").value("ADMIN"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken")
                .asText();
    }

    private record AuthSession(String accessToken, UUID userId) {
    }
}
