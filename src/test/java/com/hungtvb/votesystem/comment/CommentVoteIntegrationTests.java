package com.hungtvb.votesystem.comment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = "app.rate-limit.enabled=false")
@AutoConfigureMockMvc
class CommentVoteIntegrationTests {
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

    @Test
    void addChangeRemoveAndListMyVoteRemainConsistent() throws Exception {
        AuthSession author = register("comment-vote-author@example.com");
        AuthSession voter = register("comment-vote-voter@example.com");
        UUID postId = createPost(author, "Comment vote lifecycle");
        UUID commentId = createComment(author, postId, "Vote on this argument");

        cast(voter, commentId, "UP")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voteScore").value(1))
                .andExpect(jsonPath("$.upVotes").value(1))
                .andExpect(jsonPath("$.downVotes").value(0))
                .andExpect(jsonPath("$.myVote").value("UP"));
        cast(voter, commentId, "UP")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voteScore").value(1))
                .andExpect(jsonPath("$.upVotes").value(1));
        cast(voter, commentId, "DOWN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voteScore").value(-1))
                .andExpect(jsonPath("$.upVotes").value(0))
                .andExpect(jsonPath("$.downVotes").value(1))
                .andExpect(jsonPath("$.myVote").value("DOWN"));

        mockMvc.perform(get("/api/v1/posts/{postId}/comments", postId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(voter.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].voteScore").value(-1))
                .andExpect(jsonPath("$.content[0].upVotes").value(0))
                .andExpect(jsonPath("$.content[0].downVotes").value(1))
                .andExpect(jsonPath("$.content[0].myVote").value("DOWN"));
        mockMvc.perform(get("/api/v1/posts/{postId}/comments", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].myVote").doesNotExist());

        mockMvc.perform(delete("/api/v1/comments/{commentId}/vote", commentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(voter.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voteScore").value(0))
                .andExpect(jsonPath("$.upVotes").value(0))
                .andExpect(jsonPath("$.downVotes").value(0))
                .andExpect(jsonPath("$.myVote").doesNotExist());
        mockMvc.perform(delete("/api/v1/comments/{commentId}/vote", commentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(voter.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voteScore").value(0));

        assertEquals(0L, jdbcTemplate.queryForObject(
                "select count(*) from comment_votes where comment_id = ?", Long.class, commentId));
        assertAggregate(commentId, 0, 0, 0);
    }

    @Test
    void concurrentDuplicateVoteFromSameUserRemainsIdempotent() throws Exception {
        AuthSession author = register("comment-vote-duplicate-author@example.com");
        AuthSession voter = register("comment-vote-duplicate-voter@example.com");
        UUID postId = createPost(author, "Duplicate concurrent comment voting");
        UUID commentId = createComment(author, postId, "Duplicate concurrent target");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < 2; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return mockMvc.perform(put("/api/v1/comments/{commentId}/vote", commentId)
                                    .header(HttpHeaders.AUTHORIZATION, bearer(voter.accessToken()))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"type\":\"UP\"}"))
                            .andReturn().getResponse().getStatus();
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            for (Future<Integer> future : futures) {
                assertEquals(200, future.get(30, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1L, jdbcTemplate.queryForObject(
                "select count(*) from comment_votes where comment_id = ? and user_id = ?",
                Long.class,
                commentId,
                voter.userId()));
        assertAggregate(commentId, 1, 1, 0);
    }

    @Test
    void concurrentDistinctUsersCannotCorruptAggregates() throws Exception {
        AuthSession author = register("comment-vote-concurrent-author@example.com");
        UUID postId = createPost(author, "Concurrent comment voting");
        UUID commentId = createComment(author, postId, "Concurrent target");
        List<AuthSession> voters = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            voters.add(register("comment-vote-concurrent-" + index + "@example.com"));
        }

        ExecutorService executor = Executors.newFixedThreadPool(voters.size());
        CountDownLatch ready = new CountDownLatch(voters.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (AuthSession voter : voters) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return mockMvc.perform(put("/api/v1/comments/{commentId}/vote", commentId)
                                    .header(HttpHeaders.AUTHORIZATION, bearer(voter.accessToken()))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"type\":\"UP\"}"))
                            .andReturn().getResponse().getStatus();
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            for (Future<Integer> future : futures) {
                assertEquals(200, future.get(30, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(5L, jdbcTemplate.queryForObject(
                "select count(*) from comment_votes where comment_id = ?", Long.class, commentId));
        assertAggregate(commentId, 5, 5, 0);
    }

    private org.springframework.test.web.servlet.ResultActions cast(AuthSession voter,
                                                                     UUID commentId,
                                                                     String type) throws Exception {
        return mockMvc.perform(put("/api/v1/comments/{commentId}/vote", commentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(voter.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"" + type + "\"}"));
    }

    private void assertAggregate(UUID commentId, long score, long up, long down) {
        JsonNode row = jdbcTemplate.queryForObject("""
                select vote_score, up_votes, down_votes
                  from comments
                 where id = ?
                """, (resultSet, rowNum) -> objectMapper.createObjectNode()
                .put("score", resultSet.getLong("vote_score"))
                .put("up", resultSet.getLong("up_votes"))
                .put("down", resultSet.getLong("down_votes")), commentId);
        assertEquals(score, row.get("score").asLong());
        assertEquals(up, row.get("up").asLong());
        assertEquals(down, row.get("down").asLong());
        assertEquals(score, up - down);
    }

    private AuthSession register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"strong-password\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode payload = objectMapper.readTree(result.getResponse().getContentAsString());
        return new AuthSession(payload.get("accessToken").asText(),
                UUID.fromString(payload.get("profile").get("id").asText()));
    }

    private UUID createPost(AuthSession author, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(author.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"content\":\"Body\",\"category\":\"GENERAL\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private UUID createComment(AuthSession author, UUID postId, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"" + body + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.voteScore").value(0))
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private String bearer(String token) { return "Bearer " + token; }

    private record AuthSession(String accessToken, UUID userId) { }
}
