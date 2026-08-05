package com.hungtvb.votesystem.admin.moderation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hungtvb.votesystem.admin.AdminBootstrapService;
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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = "app.rate-limit.enabled=false")
@AutoConfigureMockMvc
class AdminCommentModerationIntegrationTests {
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
    @Autowired AdminCommentModerationService moderationService;

    @Test
    void adminBoundaryRejectsAnonymousAndUser() throws Exception {
        UUID commentId = UUID.randomUUID();
        String body = reason("Published comment policy violation");
        mockMvc.perform(post("/api/v1/admin/comments/{commentId}/hide", commentId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        AuthSession user = register("comment-moderation-boundary@example.com");
        mockMvc.perform(post("/api/v1/admin/comments/{commentId}/hide", commentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void hideRestoreAndDeleteAreAuditedAtomicAndPubliclyRedacted() throws Exception {
        AuthSession admin = admin("comment-moderation-admin@example.com");
        AuthSession author = register("comment-moderation-author@example.com");
        AuthSession voter = register("comment-moderation-voter@example.com");
        UUID postId = createPost(author);
        UUID commentId = createComment(author, postId, "Sensitive body retained only in storage");

        mockMvc.perform(put("/api/v1/comments/{commentId}/vote", commentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(voter.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"UP\"}"))
                .andExpect(status().isOk());

        moderate(admin, commentId, "hide", "Confirmed comment violation", "HIDDEN")
                .andExpect(jsonPath("$.voteScore").value(1));
        assertEquals(0L, commentCount(postId));
        mockMvc.perform(get("/api/v1/posts/{postId}/comments", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].moderationStatus").value("HIDDEN"))
                .andExpect(jsonPath("$.content[0].body").doesNotExist())
                .andExpect(jsonPath("$.content[0].voteScore").value(1));
        mockMvc.perform(put("/api/v1/comments/{commentId}/vote", commentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(voter.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"DOWN\"}"))
                .andExpect(status().isNotFound());

        moderate(admin, commentId, "restore", "Review completed", "VISIBLE");
        assertEquals(1L, commentCount(postId));
        mockMvc.perform(get("/api/v1/posts/{postId}/comments", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].body").value("Sensitive body retained only in storage"));

        moderate(admin, commentId, "remove", "Terminal removal required", "DELETED");
        assertEquals(0L, commentCount(postId));
        moderate(admin, commentId, "restore", "Cannot restore deleted", null)
                .andExpect(status().isConflict());

        assertEquals(1L, auditCount(commentId, "ADMIN_HIDE_COMMENT"));
        assertEquals(1L, auditCount(commentId, "ADMIN_RESTORE_COMMENT"));
        assertEquals(1L, auditCount(commentId, "ADMIN_DELETE_COMMENT"));
        assertEquals("Sensitive body retained only in storage", jdbcTemplate.queryForObject(
                "select body from comments where id = ?", String.class, commentId));
    }

    @Test
    void auditValidationFailureRollsBackCommentAndCount() throws Exception {
        AuthSession admin = admin("comment-moderation-rollback-admin@example.com");
        AuthSession author = register("comment-moderation-rollback-author@example.com");
        UUID postId = createPost(author);
        UUID commentId = createComment(author, postId, "Rollback body");

        assertThrows(IllegalArgumentException.class, () -> moderationService.hide(
                admin.userId(), commentId, "x".repeat(501)));
        assertEquals("VISIBLE", jdbcTemplate.queryForObject(
                "select moderation_status from comments where id = ?", String.class, commentId));
        assertEquals(1L, commentCount(postId));
        assertEquals(0L, auditCount(commentId, "ADMIN_HIDE_COMMENT"));
    }

    private org.springframework.test.web.servlet.ResultActions moderate(AuthSession admin,
                                                                         UUID commentId,
                                                                         String action,
                                                                         String reason,
                                                                         String expectedStatus) throws Exception {
        var result = mockMvc.perform(post("/api/v1/admin/comments/{commentId}/{action}", commentId, action)
                .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(reason(reason)));
        if (expectedStatus != null) {
            result.andExpect(status().isOk()).andExpect(jsonPath("$.moderationStatus").value(expectedStatus));
        }
        return result;
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
                .andExpect(status().isCreated()).andReturn();
        JsonNode payload = objectMapper.readTree(result.getResponse().getContentAsString());
        return new AuthSession(payload.get("accessToken").asText(),
                UUID.fromString(payload.get("profile").get("id").asText()));
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"strong-password\"}"))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private UUID createPost(AuthSession author) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(author.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Comment moderation\",\"content\":\"Body\",\"category\":\"GENERAL\"}"))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private UUID createComment(AuthSession author, UUID postId, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"" + body + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private String reason(String reason) throws Exception {
        return objectMapper.writeValueAsString(new Reason(reason));
    }

    private long commentCount(UUID postId) {
        return jdbcTemplate.queryForObject("select comment_count from posts where id = ?", Long.class, postId);
    }

    private long auditCount(UUID commentId, String action) {
        return jdbcTemplate.queryForObject("""
                select count(*) from admin_audit_logs
                 where target_type = 'COMMENT' and target_id = ? and action = ?
                """, Long.class, commentId.toString(), action);
    }

    private String bearer(String token) { return "Bearer " + token; }
    private record AuthSession(String accessToken, UUID userId) { }
    private record Reason(String reason) { }
}
