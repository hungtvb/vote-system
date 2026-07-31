package com.hungtvb.votesystem.admin.ranking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hungtvb.votesystem.admin.AdminBootstrapService;
import com.hungtvb.votesystem.admin.ranking.dto.AdminRankingRebuildRequest;
import com.hungtvb.votesystem.admin.ranking.dto.AdminRankingStatusResponse;
import com.hungtvb.votesystem.common.error.ConflictException;
import com.hungtvb.votesystem.post.Post;
import com.hungtvb.votesystem.post.PostRepository;
import com.hungtvb.votesystem.ranking.FeedType;
import com.hungtvb.votesystem.ranking.RedisRankingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = "app.rate-limit.enabled=false")
@AutoConfigureMockMvc
class AdminRankingIntegrationTests {

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
    @Autowired AdminRankingService adminRankingService;
    @Autowired AdminRankingController adminRankingController;
    @MockitoSpyBean RedisRankingRepository rankingRepository;
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired PostRepository postRepository;

    @BeforeEach
    void clearRankingStore() {
        reset(rankingRepository);
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void adminBoundaryRejectsAnonymousAndUser() throws Exception {
        mockMvc.perform(get("/api/v1/admin/rankings/status"))
                .andExpect(status().isUnauthorized());

        AuthSession user = register("ranking-boundary-user@example.com");
        mockMvc.perform(get("/api/v1/admin/rankings/status")
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/rankings/rebuild")
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reason("Routine ranking rebuild")))
                .andExpect(status().isForbidden());
    }

    @Test
    void successfulRebuildPublishesVisibleGenerationAndAppendsOneAudit() throws Exception {
        AuthSession admin = admin("ranking-success-admin@example.com");
        AuthSession author = register("ranking-success-author@example.com");
        UUID visiblePost = createPost(author.accessToken(), "Visible ranking ballot");
        UUID hiddenPost = createPost(author.accessToken(), "Hidden ranking ballot");

        mockMvc.perform(post("/api/v1/admin/posts/{postId}/hide", hiddenPost)
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reason("Hide before ranking rebuild")))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(post("/api/v1/admin/rankings/rebuild")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reason("Rebuild after moderation review")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability").value("HEALTHY"))
                .andExpect(jsonPath("$.rebuildInProgress").value(false))
                .andReturn();

        JsonNode payload = objectMapper.readTree(result.getResponse().getContentAsString());
        String generation = payload.get("generation").asText();
        assertFalse(generation.isBlank());
        assertEquals(payload.get("visibleBallots").asLong(), payload.get("hotMembers").asLong());
        assertTrue(rankingRepository.range(FeedType.HOT, 0, 500, Instant.now()).contains(visiblePost));
        assertFalse(rankingRepository.range(FeedType.HOT, 0, 500, Instant.now()).contains(hiddenPost));
        assertEquals(1L, auditCount());

        mockMvc.perform(get("/api/v1/admin/rankings/status")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generation").value(generation))
                .andExpect(jsonPath("$.availability").value("HEALTHY"));
    }

    @Test
    void concurrentVoteAfterSnapshotIsRetriedAndPublishedWithoutLostScore() throws Exception {
        AuthSession admin = admin("ranking-race-admin@example.com");
        AuthSession author = register("ranking-race-author@example.com");
        AuthSession voter = register("ranking-race-voter@example.com");
        UUID postId = createPost(author.accessToken(), "Concurrent vote ranking ballot");
        adminRankingService.rebuild(admin.userId(), "Publish initial generation");
        long auditBefore = auditCount();

        CountDownLatch snapshotPrepared = new CountDownLatch(1);
        CountDownLatch voteCommitted = new CountDownLatch(1);
        AtomicBoolean blockFirstAttempt = new AtomicBoolean(true);
        doAnswer(invocation -> {
            RedisRankingRepository.RankingCounts counts =
                    (RedisRankingRepository.RankingCounts) invocation.callRealMethod();
            if (blockFirstAttempt.compareAndSet(true, false)) {
                snapshotPrepared.countDown();
                if (!voteCommitted.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("Concurrent vote did not commit before the rebuild fence");
                }
            }
            return counts;
        }).when(rankingRepository).generationCounts(any(RedisRankingRepository.RankingGeneration.class));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<AdminRankingStatusResponse> rebuild = executor.submit(() ->
                    adminRankingService.rebuild(admin.userId(), "Rebuild across a concurrent vote"));
            assertTrue(snapshotPrepared.await(10, TimeUnit.SECONDS));
            try {
                mockMvc.perform(put("/api/v1/posts/{postId}/vote", postId)
                                .header("Authorization", "Bearer " + voter.accessToken())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"type\":\"UP\"}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.voteScore").value(1));
            } finally {
                voteCommitted.countDown();
            }

            AdminRankingStatusResponse response = rebuild.get(20, TimeUnit.SECONDS);
            assertEquals("HEALTHY", response.availability().name());
            assertEquals(auditBefore + 1, auditCount());
            assertEquals(1.0d, publishedDayScore(postId));
        } finally {
            voteCommitted.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void repeatedConcurrentCreatesExhaustRetryBudgetWithoutPublishingOrAuditing() throws Exception {
        AuthSession admin = admin("ranking-retry-admin@example.com");
        AuthSession author = register("ranking-retry-author@example.com");
        createPost(author.accessToken(), "Initial stable ballot");
        AdminRankingStatusResponse initial = adminRankingService.rebuild(
                admin.userId(), "Publish generation before retry exhaustion");
        long auditBefore = auditCount();
        AtomicInteger attempts = new AtomicInteger();

        doAnswer(invocation -> {
            RedisRankingRepository.RankingCounts counts =
                    (RedisRankingRepository.RankingCounts) invocation.callRealMethod();
            int attempt = attempts.incrementAndGet();
            createPost(author.accessToken(), "Concurrent ballot " + attempt);
            return counts;
        }).when(rankingRepository).generationCounts(any(RedisRankingRepository.RankingGeneration.class));

        ConflictException exception = assertThrows(ConflictException.class, () ->
                adminRankingService.rebuild(admin.userId(), "This rebuild should remain unstable"));

        assertEquals("Ranking changed repeatedly during rebuild; retry later", exception.getMessage());
        assertEquals(3, attempts.get());
        assertEquals(initial.generation(), adminRankingService.status().generation());
        assertEquals(auditBefore, auditCount());
        assertFalse(adminRankingService.status().rebuildInProgress());
    }

    @Test
    void heldDistributedLockRejectsDuplicateRebuildWithoutAudit() throws Exception {
        AuthSession admin = admin("ranking-lock-admin@example.com");
        String lockToken = UUID.randomUUID().toString();
        assertTrue(rankingRepository.tryAcquireRebuildLock(lockToken, Duration.ofMinutes(1)));
        long before = auditCount();
        try {
            mockMvc.perform(post("/api/v1/admin/rankings/rebuild")
                            .header("Authorization", "Bearer " + admin.accessToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(reason("Duplicate rebuild should be rejected")))
                    .andExpect(status().isConflict());
            assertEquals(before, auditCount());
        } finally {
            rankingRepository.releaseRebuildLock(lockToken);
        }
    }

    @Test
    void auditFailureRollsPointerBackToPreviouslyPublishedGeneration() throws Exception {
        AuthSession admin = admin("ranking-rollback-admin@example.com");
        AuthSession author = register("ranking-rollback-author@example.com");
        createPost(author.accessToken(), "Initial ranking ballot");

        AdminRankingStatusResponse initial = adminRankingService.rebuild(admin.userId(), "Publish initial generation");
        assertNotNull(initial.generation());
        long initialMembers = initial.hotMembers();
        long auditBefore = auditCount();

        Post extra = Post.create(author.userId(), "Unpublished extra ballot", "Ranking rollback test",
                "GENERAL", null, 70);
        postRepository.saveAndFlush(extra);

        assertThrows(IllegalArgumentException.class,
                () -> adminRankingService.rebuild(null, "Audit actor validation must fail"));

        AdminRankingStatusResponse after = adminRankingService.status();
        assertEquals(initial.generation(), after.generation());
        assertEquals(initialMembers, after.hotMembers());
        assertNotEquals(after.visibleBallots(), after.hotMembers());
        assertEquals(auditBefore, auditCount());
        assertFalse(after.rebuildInProgress());
    }

    @Test
    void blankReasonIsRejectedBeforeRebuild() throws Exception {
        AuthSession admin = admin("ranking-reason-admin@example.com");
        mockMvc.perform(post("/api/v1/admin/rankings/rebuild")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    void methodGuardRejectsDirectUserInvocation() {
        assertThrows(AccessDeniedException.class, adminRankingController::status);
        assertThrows(AccessDeniedException.class, () -> adminRankingController.rebuild(
                null,
                new AdminRankingRebuildRequest("Direct invocation must be denied")
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
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private UUID createPost(String token, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PostPayload(title, "Ranking integration test", "GENERAL"))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private String reason(String value) throws Exception {
        return objectMapper.writeValueAsString(new AdminRankingRebuildRequest(value));
    }

    private long auditCount() {
        return jdbcTemplate.queryForObject(
                "select count(*) from admin_audit_logs where action = 'ADMIN_REBUILD_RANKING' and target_id = 'ALL'",
                Long.class
        );
    }

    private double publishedDayScore(UUID postId) {
        String generation = redisTemplate.opsForValue().get("feed:{ranking}:active-generation");
        assertNotNull(generation);
        Double score = redisTemplate.opsForZSet().score(
                "feed:{ranking}:generation:" + generation + ":day",
                postId.toString()
        );
        assertNotNull(score);
        return score;
    }

    private record AuthSession(String accessToken, UUID userId) {
    }

    private record PostPayload(String title, String content, String category) {
    }
}
