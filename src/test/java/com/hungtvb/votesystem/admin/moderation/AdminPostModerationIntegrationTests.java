package com.hungtvb.votesystem.admin.moderation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hungtvb.votesystem.admin.AdminBootstrapService;
import com.hungtvb.votesystem.admin.moderation.dto.AdminModerationReasonRequest;
import com.hungtvb.votesystem.common.error.ConflictException;
import com.hungtvb.votesystem.post.Post;
import com.hungtvb.votesystem.post.PostRepository;
import com.hungtvb.votesystem.ranking.FeedType;
import com.hungtvb.votesystem.ranking.RedisRankingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
class AdminPostModerationIntegrationTests {

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
    @Autowired AdminPostModerationService moderationService;
    @Autowired AdminPostModerationController moderationController;
    @Autowired PostRepository postRepository;
    @Autowired RedisRankingRepository rankingRepository;

    @Test
    void adminBoundaryRejectsAnonymousAndUser() throws Exception {
        UUID postId = UUID.randomUUID();
        String request = reason("Published community rule violation");

        mockMvc.perform(post("/api/v1/admin/posts/{postId}/hide", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isUnauthorized());

        AuthSession user = register("moderation-boundary-user@example.com");
        mockMvc.perform(post("/api/v1/admin/posts/{postId}/hide", postId)
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isForbidden());
    }

    @Test
    void hideRemovesBallotFromEveryPublicSurfaceEvenWithStaleRedisMember() throws Exception {
        AuthSession admin = admin("moderation-hide-admin@example.com");
        AuthSession author = register("moderation-hide-author@example.com");
        AuthSession voter = register("moderation-hide-voter@example.com");
        UUID postId = createPost(author.accessToken(), "Hidden ballot", "Moderation visibility", "GENERAL");

        mockMvc.perform(post("/api/v1/admin/posts/{postId}/hide", postId)
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reason("Violates published community rules")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(postId.toString()))
                .andExpect(jsonPath("$.moderationStatus").value("HIDDEN"));

        mockMvc.perform(get("/api/v1/posts/{postId}", postId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Post not found"));
        mockMvc.perform(get("/api/v1/posts/{postId}/events", postId))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/posts/{postId}/vote", postId)
                        .header("Authorization", "Bearer " + voter.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"UP\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/posts/{postId}/close", postId)
                        .header("Authorization", "Bearer " + author.accessToken()))
                .andExpect(status().isNotFound());

        assertFeedDoesNotContain(postId, null, null);
        assertFeedDoesNotContain(postId, "MINE", author.accessToken());

        awaitRankingAbsent(postId);
        Post post = postRepository.findById(postId).orElseThrow();
        rankingRepository.upsert(postId, post.getVoteScore(), 100.0, post.getCreatedAt(), Instant.now());
        assertTrue(rankingRepository.range(FeedType.HOT, 0, 100, Instant.now()).contains(postId));
        assertFeedDoesNotContain(postId, "HOT", null);

        assertEquals(1L, auditCount(postId, "ADMIN_HIDE_POST"));
        assertEquals("HIDDEN", moderationStatus(postId));
    }

    @Test
    void restoreMakesAnOpenBallotPublicAndVoteableAgain() throws Exception {
        AuthSession admin = admin("moderation-restore-admin@example.com");
        AuthSession author = register("moderation-restore-author@example.com");
        AuthSession voter = register("moderation-restore-voter@example.com");
        UUID postId = createPost(author.accessToken(), "Restored ballot", "Restore path", "GENERAL");

        moderate(admin.accessToken(), postId, "hide", "Temporary review hold", "HIDDEN");
        moderate(admin.accessToken(), postId, "restore", "Review completed", "VISIBLE");

        mockMvc.perform(get("/api/v1/posts/{postId}", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
        mockMvc.perform(put("/api/v1/posts/{postId}/vote", postId)
                        .header("Authorization", "Bearer " + voter.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"UP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upVotes").value(1));

        assertEquals(1L, auditCount(postId, "ADMIN_HIDE_POST"));
        assertEquals(1L, auditCount(postId, "ADMIN_RESTORE_POST"));
    }

    @Test
    void restorePreservesClosedLifecycleState() throws Exception {
        AuthSession admin = admin("moderation-closed-admin@example.com");
        AuthSession author = register("moderation-closed-author@example.com");
        AuthSession voter = register("moderation-closed-voter@example.com");
        UUID postId = createPost(author.accessToken(), "Closed restored ballot", "Lifecycle stays closed", "GENERAL");

        mockMvc.perform(post("/api/v1/posts/{postId}/close", postId)
                        .header("Authorization", "Bearer " + author.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        moderate(admin.accessToken(), postId, "hide", "Temporary review hold", "HIDDEN");
        moderate(admin.accessToken(), postId, "restore", "Review completed", "VISIBLE");

        mockMvc.perform(get("/api/v1/posts/{postId}", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
        mockMvc.perform(put("/api/v1/posts/{postId}/vote", postId)
                        .header("Authorization", "Bearer " + voter.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"UP\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void softDeleteIsTerminalAndPreservesBallotAndVotes() throws Exception {
        AuthSession admin = admin("moderation-delete-admin@example.com");
        AuthSession author = register("moderation-delete-author@example.com");
        AuthSession voter = register("moderation-delete-voter@example.com");
        UUID postId = createPost(author.accessToken(), "Soft deleted ballot", "Rows remain", "GENERAL");

        mockMvc.perform(put("/api/v1/posts/{postId}/vote", postId)
                        .header("Authorization", "Bearer " + voter.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"UP\"}"))
                .andExpect(status().isOk());

        moderate(admin.accessToken(), postId, "delete", "Confirmed destructive moderation", "DELETED");

        mockMvc.perform(get("/api/v1/posts/{postId}", postId))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/admin/posts/{postId}/restore", postId)
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reason("Deleted ballots are terminal")))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/v1/posts/{postId}/vote", postId)
                        .header("Authorization", "Bearer " + voter.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"UP\"}"))
                .andExpect(status().isNotFound());

        assertEquals(1L, jdbcTemplate.queryForObject(
                "select count(*) from posts where id = ?", Long.class, postId));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "select count(*) from votes where post_id = ?", Long.class, postId));
        assertEquals(1L, auditCount(postId, "ADMIN_DELETE_POST"));
        assertEquals("DELETED", moderationStatus(postId));
    }

    @Test
    void auditValidationFailureRollsBackModerationState() throws Exception {
        AuthSession admin = admin("moderation-rollback-admin@example.com");
        AuthSession author = register("moderation-rollback-author@example.com");
        UUID postId = createPost(author.accessToken(), "Rollback ballot", "Audit failure rollback", "GENERAL");

        assertThrows(IllegalArgumentException.class, () ->
                moderationService.hide(admin.userId(), postId, "x".repeat(501)));

        assertEquals("VISIBLE", moderationStatus(postId));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "select count(*) from admin_audit_logs where target_id = ?",
                Long.class,
                postId.toString()));
    }

    @Test
    void concurrentHideRequestsProduceOneTransitionAndOneAuditRecord() throws Exception {
        AuthSession admin = admin("moderation-concurrent-admin@example.com");
        AuthSession author = register("moderation-concurrent-author@example.com");
        UUID postId = createPost(author.accessToken(), "Concurrent moderation", "One transition", "GENERAL");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                String reason = "Concurrent review " + index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    try {
                        moderationService.hide(admin.userId(), postId, reason);
                        return "success";
                    } catch (ConflictException exception) {
                        return "conflict";
                    }
                }));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            Set<String> outcomes = Set.of(
                    futures.get(0).get(10, TimeUnit.SECONDS),
                    futures.get(1).get(10, TimeUnit.SECONDS));

            assertEquals(Set.of("success", "conflict"), outcomes);
            assertEquals("HIDDEN", moderationStatus(postId));
            assertEquals(1L, auditCount(postId, "ADMIN_HIDE_POST"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @WithMockUser(roles = "USER")
    void methodGuardRejectsDirectUserInvocation() {
        assertThrows(AccessDeniedException.class, () -> moderationController.hide(
                null,
                UUID.randomUUID(),
                new AdminModerationReasonRequest("Moderation reason")
        ));
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

    private UUID createPost(String token, String title, String content, String category) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PostPayload(title, content, category))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private void moderate(String token,
                          UUID postId,
                          String action,
                          String reason,
                          String expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/admin/posts/{postId}/{action}", postId, action)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reason(reason)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moderationStatus").value(expectedStatus));
    }

    private void assertFeedDoesNotContain(UUID postId, String feed, String token) throws Exception {
        var request = get("/api/v1/posts").param("size", "100");
        if (feed != null) {
            request.param("feed", feed);
        }
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();
        JsonNode content = objectMapper.readTree(result.getResponse().getContentAsString()).get("content");
        for (JsonNode item : content) {
            assertFalse(postId.toString().equals(item.get("id").asText()));
        }
    }

    private void awaitRankingAbsent(UUID postId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (!rankingRepository.range(FeedType.HOT, 0, 100, Instant.now()).contains(postId)) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for ranking removal");
    }

    private long auditCount(UUID postId, String action) {
        return jdbcTemplate.queryForObject(
                "select count(*) from admin_audit_logs where target_id = ? and action = ?",
                Long.class,
                postId.toString(),
                action
        );
    }

    private String moderationStatus(UUID postId) {
        return jdbcTemplate.queryForObject(
                "select moderation_status from posts where id = ?",
                String.class,
                postId
        );
    }

    private String reason(String value) throws Exception {
        return objectMapper.writeValueAsString(new AdminModerationReasonRequest(value));
    }

    private record AuthSession(String accessToken, UUID userId) {
    }

    private record PostPayload(String title, String content, String category) {
    }
}
