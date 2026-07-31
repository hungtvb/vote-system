package com.hungtvb.votesystem.admin.ranking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hungtvb.votesystem.admin.AdminBootstrapService;
import com.hungtvb.votesystem.admin.ranking.dto.AdminRankingStatusResponse;
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

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = "app.rate-limit.enabled=false")
@AutoConfigureMockMvc
class RankingRevisionFenceIntegrationTests {

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
    @MockitoSpyBean RedisRankingRepository rankingRepository;
    @Autowired StringRedisTemplate redisTemplate;

    @BeforeEach
    void resetInfrastructure() {
        reset(rankingRepository);
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void concurrentCreateAfterSnapshotIsIncludedByStableRetry() throws Exception {
        AuthSession admin = admin("ranking-create-race-admin@example.com");
        AuthSession author = register("ranking-create-race-author@example.com");
        createPost(author.accessToken(), "Initial ranking ballot");
        adminRankingService.rebuild(admin.userId(), "Publish initial generation");
        long auditBefore = rankingAuditCount();
        AtomicBoolean createOnce = new AtomicBoolean(true);
        UUID[] concurrentPost = new UUID[1];

        doAnswer(invocation -> {
            RedisRankingRepository.RankingCounts counts =
                    (RedisRankingRepository.RankingCounts) invocation.callRealMethod();
            if (createOnce.compareAndSet(true, false)) {
                concurrentPost[0] = createPost(author.accessToken(), "Created during ranking rebuild");
            }
            return counts;
        }).when(rankingRepository).generationCounts(any(RedisRankingRepository.RankingGeneration.class));

        AdminRankingStatusResponse response = adminRankingService.rebuild(
                admin.userId(), "Rebuild across a concurrent create");

        assertEquals("HEALTHY", response.availability().name());
        assertEquals(auditBefore + 1, rankingAuditCount());
        assertTrue(rankingRepository.range(FeedType.HOT, 0, 500, Instant.now()).contains(concurrentPost[0]));
    }

    @Test
    void concurrentHideAfterSnapshotIsExcludedByStableRetry() throws Exception {
        AuthSession admin = admin("ranking-hide-race-admin@example.com");
        AuthSession author = register("ranking-hide-race-author@example.com");
        UUID hiddenDuringRebuild = createPost(author.accessToken(), "Hide during ranking rebuild");
        createPost(author.accessToken(), "Remain visible during ranking rebuild");
        adminRankingService.rebuild(admin.userId(), "Publish initial generation");
        long auditBefore = rankingAuditCount();
        AtomicBoolean hideOnce = new AtomicBoolean(true);

        doAnswer(invocation -> {
            RedisRankingRepository.RankingCounts counts =
                    (RedisRankingRepository.RankingCounts) invocation.callRealMethod();
            if (hideOnce.compareAndSet(true, false)) {
                hidePost(admin.accessToken(), hiddenDuringRebuild);
            }
            return counts;
        }).when(rankingRepository).generationCounts(any(RedisRankingRepository.RankingGeneration.class));

        AdminRankingStatusResponse response = adminRankingService.rebuild(
                admin.userId(), "Rebuild across concurrent moderation");

        assertEquals("HEALTHY", response.availability().name());
        assertEquals(auditBefore + 1, rankingAuditCount());
        assertFalse(rankingRepository.range(FeedType.HOT, 0, 500, Instant.now()).contains(hiddenDuringRebuild));
        assertEquals(response.visibleBallots(), response.hotMembers());
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
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private UUID createPost(String token, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PostPayload(title, "Ranking fence test", "GENERAL"))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private void hidePost(String token, UUID postId) throws Exception {
        mockMvc.perform(post("/api/v1/admin/posts/{postId}/hide", postId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Concurrent moderation fence test\"}"))
                .andExpect(status().isOk());
    }

    private long rankingAuditCount() {
        return jdbcTemplate.queryForObject(
                "select count(*) from admin_audit_logs where action = 'ADMIN_REBUILD_RANKING' and target_id = 'ALL'",
                Long.class
        );
    }

    private record AuthSession(String accessToken, UUID userId) {
    }

    private record PostPayload(String title, String content, String category) {
    }
}
