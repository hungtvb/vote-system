package com.hungtvb.votesystem.comment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.data.domain.PageRequest;
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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = "app.rate-limit.enabled=false")
@AutoConfigureMockMvc
class CommentIntegrationTests {
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
    @Autowired CommentRepository commentRepository;

    @Test
    void guestsReadOneLevelRepliesWithBallotMarkAndAuthoritativeCommentCount() throws Exception {
        AuthSession author = register("comment-author@example.com", "Discussion Author");
        UUID postId = createPost(author, "Comment foundation");
        JsonNode root = createComment(author, postId, "First argument", null);
        JsonNode reply = createComment(author, postId, "One-level reply", UUID.fromString(root.get("id").asText()));

        mockMvc.perform(get("/api/v1/posts/{postId}/comments", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(root.get("id").asText()))
                .andExpect(jsonPath("$.content[0].parentId").doesNotExist())
                .andExpect(jsonPath("$.content[0].author.displayName").value("Discussion Author"))
                .andExpect(jsonPath("$.content[0].author.avatarIcon").value("CITIZEN"))
                .andExpect(jsonPath("$.content[0].body").value("First argument"))
                .andExpect(jsonPath("$.content[1].id").value(reply.get("id").asText()))
                .andExpect(jsonPath("$.content[1].parentId").value(root.get("id").asText()));

        mockMvc.perform(get("/api/v1/posts/{postId}", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commentCount").value(2));

        mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentPayload("Reply to a reply", UUID.fromString(reply.get("id").asText()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Replies are limited to one visible parent level"));
    }

    @Test
    void ownershipEditAndIdempotentRemovalProduceSafeTombstone() throws Exception {
        AuthSession author = register("comment-owner@example.com", "Comment Owner");
        AuthSession other = register("comment-other@example.com", "Other Voter");
        UUID postId = createPost(author, "Ownership ballot");
        UUID commentId = UUID.fromString(createComment(author, postId, "Original body", null).get("id").asText());

        mockMvc.perform(patch("/api/v1/comments/{commentId}", commentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Unauthorized edit\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/comments/{commentId}", commentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.accessToken())))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/comments/{commentId}", commentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"  Edited body  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Edited body"))
                .andExpect(jsonPath("$.editedAt").exists())
                .andExpect(jsonPath("$.ownedByCurrentUser").value(true));

        mockMvc.perform(delete("/api/v1/comments/{commentId}", commentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author.accessToken())))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/comments/{commentId}", commentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author.accessToken())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/posts/{postId}/comments", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].moderationStatus").value("REMOVED_BY_AUTHOR"))
                .andExpect(jsonPath("$.content[0].body").doesNotExist())
                .andExpect(jsonPath("$.content[0].ownedByCurrentUser").value(false));
        mockMvc.perform(get("/api/v1/posts/{postId}", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commentCount").value(0));
        assertEquals("Edited body", jdbcTemplate.queryForObject(
                "select body from comments where id = ?", String.class, commentId));
    }

    @Test
    void ascendingCursorRemainsStableWhenNewCommentsArrive() throws Exception {
        AuthSession author = register("comment-cursor@example.com", "Cursor Author");
        UUID postId = createPost(author, "Cursor ballot");
        UUID first = UUID.fromString(createComment(author, postId, "First", null).get("id").asText());
        Thread.sleep(5);
        UUID second = UUID.fromString(createComment(author, postId, "Second", null).get("id").asText());
        Thread.sleep(5);
        UUID third = UUID.fromString(createComment(author, postId, "Third", null).get("id").asText());

        JsonNode pageOne = json(mockMvc.perform(get("/api/v1/posts/{postId}/comments", postId)
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn());
        Set<UUID> firstPageIds = ids(pageOne);
        assertEquals(Set.of(first, second), firstPageIds);

        Thread.sleep(5);
        UUID inserted = UUID.fromString(createComment(author, postId, "Inserted after cursor", null).get("id").asText());
        JsonNode pageTwo = json(mockMvc.perform(get("/api/v1/posts/{postId}/comments", postId)
                        .param("limit", "2")
                        .param("afterCreatedAt", pageOne.get("nextAfterCreatedAt").asText())
                        .param("afterId", pageOne.get("nextAfterId").asText()))
                .andExpect(status().isOk())
                .andReturn());
        Set<UUID> secondPageIds = ids(pageTwo);
        assertEquals(Set.of(third, inserted), secondPageIds);
        assertTrue(firstPageIds.stream().noneMatch(secondPageIds::contains));

        mockMvc.perform(get("/api/v1/posts/{postId}/comments", postId)
                        .param("afterCreatedAt", Instant.now().toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void concurrentCreatesPreserveEveryRowAndCommentCount() throws Exception {
        AuthSession author = register("comment-concurrent@example.com", "Concurrent Author");
        UUID postId = createPost(author, "Concurrent comments");
        int workers = 6;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        Set<Future<Integer>> futures = new HashSet<>();
        for (int index = 0; index < workers; index++) {
            int number = index;
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                return mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(author.accessToken()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(commentPayload("Concurrent " + number, null)))
                        .andReturn().getResponse().getStatus();
            }));
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        for (Future<Integer> future : futures) {
            assertEquals(201, future.get(30, TimeUnit.SECONDS));
        }
        executor.shutdownNow();

        assertEquals((long) workers, jdbcTemplate.queryForObject(
                "select count(*) from comments where post_id = ?", Long.class, postId));
        assertEquals((long) workers, jdbcTemplate.queryForObject(
                "select comment_count from posts where id = ?", Long.class, postId));
    }

    @Test
    void repositoryCursorQueryCannotExposeCommentsAfterBallotBecomesHidden() throws Exception {
        AuthSession author = register("comment-hidden-race@example.com", "Hidden Author");
        UUID postId = createPost(author, "Hidden comments");
        createComment(author, postId, "Must not leak after hide", null);

        int updated = jdbcTemplate.update("""
                update posts
                   set moderation_status = 'HIDDEN',
                       moderation_updated_at = now()
                 where id = cast(? as uuid)
                """, postId.toString());
        assertEquals(1, updated);
        assertEquals("HIDDEN", jdbcTemplate.queryForObject(
                "select moderation_status from posts where id = cast(? as uuid)",
                String.class,
                postId.toString()));

        assertTrue(commentRepository.findPage(
                postId, null, null, PageRequest.of(0, 10)).isEmpty());
        mockMvc.perform(get("/api/v1/posts/{postId}/comments", postId))
                .andExpect(status().isNotFound());
    }

    @Test
    void commentReportsAreVerifiedAndRemovedOrSelfOwnedTargetsAreRejected() throws Exception {
        AuthSession ballotOwner = register("comment-report-owner@example.com", "Ballot Owner");
        AuthSession commenter = register("comment-report-commenter@example.com", "Commenter");
        UUID postId = createPost(ballotOwner, "Reportable comments");
        UUID commentId = UUID.fromString(createComment(commenter, postId, "Reportable body", null).get("id").asText());

        mockMvc.perform(post("/api/v1/reports")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ballotOwner.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportPayload(commentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetType").value("COMMENT"))
                .andExpect(jsonPath("$.targetValidationStatus").value("VERIFIED"));

        mockMvc.perform(post("/api/v1/reports")
                        .header(HttpHeaders.AUTHORIZATION, bearer(commenter.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportPayload(commentId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Users cannot report their own comment"));

        mockMvc.perform(delete("/api/v1/comments/{commentId}", commentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(commenter.accessToken())))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/reports")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ballotOwner.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReportPayload(
                                "COMMENT", commentId, "SPAM", "New report after removal"))))
                .andExpect(status().isNotFound());
    }

    private AuthSession register(String email, String displayName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegistrationPayload(
                                email, "strong-password", displayName))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode payload = json(result);
        return new AuthSession(
                payload.get("accessToken").asText(),
                UUID.fromString(payload.get("profile").get("id").asText())
        );
    }

    private UUID createPost(AuthSession author, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(author.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PostPayload(
                                title, "Structured discussion", "COMMUNITY"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.commentCount").value(0))
                .andReturn();
        return UUID.fromString(json(result).get("id").asText());
    }

    private JsonNode createComment(AuthSession author,
                                   UUID postId,
                                   String body,
                                   UUID parentId) throws Exception {
        return json(mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentPayload(body, parentId)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private String commentPayload(String body, UUID parentId) throws Exception {
        return objectMapper.writeValueAsString(new CommentPayload(body, parentId));
    }

    private String reportPayload(UUID commentId) throws Exception {
        return objectMapper.writeValueAsString(new ReportPayload(
                "COMMENT", commentId, "HARASSMENT", "Concrete comment evidence"));
    }

    private Set<UUID> ids(JsonNode page) {
        Set<UUID> ids = new HashSet<>();
        page.get("content").forEach(node -> ids.add(UUID.fromString(node.get("id").asText())));
        return ids;
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record AuthSession(String accessToken, UUID userId) {
    }

    private record RegistrationPayload(String email, String password, String displayName) {
    }

    private record PostPayload(String title, String content, String category) {
    }

    private record CommentPayload(String body, UUID parentId) {
    }

    private record ReportPayload(String targetType, UUID targetId, String reasonCode, String evidenceText) {
    }
}
