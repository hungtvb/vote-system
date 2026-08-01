package com.hungtvb.votesystem.admin.ranking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hungtvb.votesystem.post.Post;
import com.hungtvb.votesystem.post.PostRepository;
import com.hungtvb.votesystem.ranking.RankingRebuildResult;
import com.hungtvb.votesystem.ranking.RankingService;
import com.hungtvb.votesystem.ranking.RedisRankingRepository;
import com.hungtvb.votesystem.system.SystemStatusService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisCallback;
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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {
        "app.rate-limit.enabled=false",
        "app.ranking.rebuild.batch-size=40",
        "app.ranking.rebuild.lock-ttl=PT0.4S",
        "app.ranking.rebuild.lock-renew-interval=PT0.08S"
})
@AutoConfigureMockMvc
class RankingRebuildScalabilityIntegrationTests {
    private static final Pattern COMMAND_CALLS = Pattern.compile("(?:^|,)calls=(\\d+)(?:,|$)");

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
    @Autowired RankingService rankingService;
    @Autowired SystemStatusService systemStatusService;
    @Autowired MeterRegistry meterRegistry;
    @Autowired StringRedisTemplate redisTemplate;
    @MockitoSpyBean PostRepository postRepository;
    @MockitoSpyBean RedisRankingRepository rankingRepository;

    @BeforeEach
    void resetInfrastructure() {
        reset(postRepository, rankingRepository);
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        jdbcTemplate.execute("truncate table users cascade");
        jdbcTemplate.update("""
                insert into system_status (
                    singleton_id, mode, message_vi, message_en, estimated_end_at,
                    updated_at, updated_by, version
                ) values (1, 'NORMAL', null, null, null, current_timestamp, null, 0)
                on conflict (singleton_id) do update
                   set mode = 'NORMAL',
                       message_vi = null,
                       message_en = null,
                       estimated_end_at = null,
                       updated_at = current_timestamp,
                       updated_by = null,
                       version = system_status.version + 1
                """);
        systemStatusService.evictCache();
        jdbcTemplate.update("update ranking_revision set revision = 0, updated_at = current_timestamp where singleton_id = 1");
    }

    @Test
    void largeRebuildUsesBoundedDatabaseAndRedisBatches() throws Exception {
        UUID authorId = register("ranking-scale-author@example.com");
        List<Post> posts = new ArrayList<>();
        for (int index = 0; index < 125; index++) {
            posts.add(Post.create(authorId, "Scale ballot " + index,
                    "Bounded ranking rebuild integration test", "GENERAL", null, 70));
        }
        postRepository.saveAllAndFlush(posts);
        clearInvocations(postRepository, rankingRepository);

        long zaddBefore = commandCalls("zadd");
        double rowsBefore = counter("vote.ranking.rebuild.rows", "result", "scanned");
        double batchesBefore = counter("vote.ranking.rebuild.redis.batches", "result", "staged");

        RankingRebuildResult result = rankingService.rebuild();

        assertEquals(125L, result.visiblePostCount());
        assertEquals(4, result.redisBatchCount());
        ArgumentCaptor<Pageable> pageRequests = ArgumentCaptor.forClass(Pageable.class);
        verify(postRepository, times(4)).findVisibleRankingBatch(pageRequests.capture());
        assertEquals(List.of(0, 1, 2, 3), pageRequests.getAllValues().stream()
                .map(Pageable::getPageNumber)
                .toList());
        assertTrue(pageRequests.getAllValues().stream().allMatch(pageable -> pageable.getPageSize() <= 40));
        verify(rankingRepository, times(4)).stageBatch(any(), any());
        assertEquals(125d, counter("vote.ranking.rebuild.rows", "result", "scanned") - rowsBefore);
        assertEquals(4d, counter("vote.ranking.rebuild.redis.batches", "result", "staged") - batchesBefore);

        // Three sentinel ZADDs initialize the staging keys, then each database batch emits
        // at most one multi-member ZADD for HOT, TOP_DAY and TOP_WEEK.
        assertEquals(15L, commandCalls("zadd") - zaddBefore);
    }

    @Test
    void generationInitializationSetsOneExpiryPerStagingKey() {
        RedisRankingRepository.RankingGeneration generation =
                rankingRepository.createGeneration(UUID.randomUUID().toString());
        long expiryBefore = expiryCommandCalls();

        rankingRepository.initializeGeneration(generation);

        assertEquals(3L, expiryCommandCalls() - expiryBefore);
        for (String key : List.of(generation.hotKey(), generation.dayKey(), generation.weekKey())) {
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            assertTrue(ttl != null && ttl > 0 && ttl <= Duration.ofMinutes(15).toSeconds());
        }
        rankingRepository.discardGeneration(generation);
    }

    @Test
    void watchdogRetainsOwnershipBeyondInitialLockTtl() throws Exception {
        UUID authorId = register("ranking-renew-author@example.com");
        postRepository.saveAndFlush(Post.create(authorId, "Renewed lock ballot",
                "The first Redis batch is deliberately slower than the initial lease", "GENERAL", null, 70));
        clearInvocations(postRepository, rankingRepository);

        AtomicBoolean delayed = new AtomicBoolean(true);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (delayed.compareAndSet(true, false)) Thread.sleep(900);
            return result;
        }).when(rankingRepository).stageBatch(any(), any());
        double renewalsBefore = counter("vote.ranking.rebuild.lock.renewals", "result", "succeeded");

        RankingRebuildResult result = rankingService.rebuild();

        assertEquals(1L, result.visiblePostCount());
        assertTrue(counter("vote.ranking.rebuild.lock.renewals", "result", "succeeded") - renewalsBefore >= 2d);
        assertFalse(rankingRepository.isRebuildInProgress());
    }

    @Test
    void expiredTokenCannotPublishRollbackOrReleaseCurrentOwner() throws Exception {
        String expiredToken = UUID.randomUUID().toString();
        String currentToken = UUID.randomUUID().toString();
        assertTrue(rankingRepository.tryAcquireRebuildLock(expiredToken, Duration.ofMillis(100)));
        Thread.sleep(220);
        assertTrue(rankingRepository.tryAcquireRebuildLock(currentToken, Duration.ofSeconds(2)));
        assertFalse(rankingRepository.renewRebuildLock(expiredToken, Duration.ofSeconds(2)));
        assertFalse(rankingRepository.releaseRebuildLock(expiredToken));
        assertTrue(rankingRepository.ownsRebuildLock(currentToken));

        RedisRankingRepository.RankingGeneration generation =
                rankingRepository.createGeneration(UUID.randomUUID().toString());
        rankingRepository.initializeGeneration(generation);
        rankingRepository.completeGeneration(generation);
        RedisRankingRepository.RankingCounts empty = new RedisRankingRepository.RankingCounts(0, 0, 0);

        assertRejected("Ranking generation publish was rejected", () ->
                rankingRepository.publishGeneration(generation, empty, expiredToken, Instant.now()));
        RedisRankingRepository.PublishState published =
                rankingRepository.publishGeneration(generation, empty, currentToken, Instant.now());
        assertRejected("Ranking generation rollback was rejected", () ->
                rankingRepository.rollbackGeneration(generation, published, expiredToken));
        assertEquals(generation.id(), rankingRepository.metadata().generation());
        assertFalse(rankingRepository.releaseRebuildLock(expiredToken));
        assertTrue(rankingRepository.ownsRebuildLock(currentToken));
        assertTrue(rankingRepository.releaseRebuildLock(currentToken));
    }

    @Test
    void failedRedisBatchPreservesPreviouslyPublishedGeneration() throws Exception {
        UUID authorId = register("ranking-failure-author@example.com");
        postRepository.saveAndFlush(Post.create(authorId, "Published ballot",
                "Initial stable generation", "GENERAL", null, 70));
        RankingRebuildResult initial = rankingService.rebuild();

        postRepository.saveAndFlush(Post.create(authorId, "Unpublished ballot",
                "This row must not replace the active generation", "GENERAL", null, 70));
        clearInvocations(rankingRepository);
        doThrow(new DataAccessResourceFailureException("Simulated Redis batch outage"))
                .when(rankingRepository).stageBatch(any(), any());

        assertThrows(DataAccessResourceFailureException.class, rankingService::rebuild);
        assertEquals(initial.generation().id(), rankingRepository.metadata().generation());
        assertEquals(initial.publishedCounts(), rankingRepository.currentCounts(Instant.now()));
        assertFalse(rankingRepository.isRebuildInProgress());
    }

    private void assertRejected(String expectedMessage, Runnable action) {
        RuntimeException failure = assertThrows(RuntimeException.class, action::run);
        Throwable root = failure;
        while (root.getCause() != null) root = root.getCause();
        assertTrue(root instanceof IllegalStateException);
        assertEquals(expectedMessage, root.getMessage());
    }

    private UUID register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"strong-password\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode payload = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(payload.get("profile").get("id").asText());
    }

    private double counter(String name, String tagName, String tagValue) {
        return meterRegistry.get(name).tag(tagName, tagValue).counter().count();
    }

    private long expiryCommandCalls() {
        return commandCalls("expire") + commandCalls("pexpire");
    }

    private long commandCalls(String command) {
        Properties stats = redisTemplate.execute((RedisCallback<Properties>) connection ->
                connection.serverCommands().info("commandstats"));
        if (stats == null) return 0;
        String value = stats.getProperty("cmdstat_" + command);
        if (value == null) return 0;
        Matcher matcher = COMMAND_CALLS.matcher(value);
        if (!matcher.find()) throw new AssertionError("Redis command stats did not contain calls: " + value);
        return Long.parseLong(matcher.group(1));
    }
}
