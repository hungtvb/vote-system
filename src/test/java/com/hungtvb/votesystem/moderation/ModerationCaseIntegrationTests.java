package com.hungtvb.votesystem.moderation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hungtvb.votesystem.admin.AdminBootstrapService;
import com.hungtvb.votesystem.admin.moderation.AdminPostModerationService;
import com.hungtvb.votesystem.moderation.admin.ModerationCaseService;
import com.hungtvb.votesystem.moderation.admin.dto.ResolveModerationCaseRequest;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = "app.rate-limit.enabled=false")
@AutoConfigureMockMvc
class ModerationCaseIntegrationTests {

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
    @Autowired ModerationCaseService caseService;
    @Autowired AdminPostModerationService postModerationService;

    @Test
    void reportSubmissionIsPrivateToReporterAndAdminQueueIsProtected() throws Exception {
        AuthSession reporter = register("report-boundary-reporter@example.com");
        AuthSession otherUser = register("report-boundary-other@example.com");
        AuthSession admin = admin("report-boundary-admin@example.com");
        UUID postId = createPost(reporter.accessToken(), "Report boundary", "Visible ballot", "GENERAL");
        String payload = reportPayload("BALLOT", postId, "SPAM", "Repeated promotional links");

        mockMvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());

        MvcResult created = mockMvc.perform(post("/api/v1/reports")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporter.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetType").value("BALLOT"))
                .andExpect(jsonPath("$.targetValidationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.caseStatus").value("OPEN"))
                .andReturn();
        UUID caseId = UUID.fromString(json(created).get("caseId").asText());

        mockMvc.perform(get("/api/v1/reports/mine")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherUser.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        mockMvc.perform(get("/api/v1/admin/moderation-cases")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporter.accessToken())))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/moderation-cases/{caseId}", caseId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moderationCase.id").value(caseId.toString()))
                .andExpect(jsonPath("$.reports.length()").value(1))
                .andExpect(jsonPath("$.reports[0].reporterId").value(reporter.userId().toString()));
    }

    @Test
    void duplicateActiveReportConflictsWhileDifferentReasonsShareOneCase() throws Exception {
        AuthSession reporter = register("report-duplicate@example.com");
        UUID postId = createPost(reporter.accessToken(), "Duplicate report", "Same active report", "GENERAL");
        String spam = reportPayload("BALLOT", postId, "SPAM", "Promotional content");

        JsonNode first = json(mockMvc.perform(post("/api/v1/reports")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporter.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(spam))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(post("/api/v1/reports")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporter.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(spam))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("An active report already exists for this target and reason"));

        JsonNode second = json(mockMvc.perform(post("/api/v1/reports")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporter.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportPayload("BALLOT", postId, "MISINFORMATION", "Material factual claim is false")))
                .andExpect(status().isCreated())
                .andReturn());

        assertEquals(first.get("caseId").asText(), second.get("caseId").asText());
        assertEquals(1L, count("select count(*) from moderation_cases where target_id = ?", postId));
        assertEquals(2L, count("select count(*) from reports where target_id = ?", postId));
        assertEquals(2, jdbcTemplate.queryForObject(
                "select report_count from moderation_cases where id = ?",
                Integer.class,
                UUID.fromString(first.get("caseId").asText())));
    }

    @Test
    void reporterHistoryUsesStableCursorWhenNewReportsArrive() throws Exception {
        AuthSession reporter = register("report-history@example.com");
        List<UUID> oldReportIds = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            UUID postId = createPost(reporter.accessToken(), "History " + index, "History content " + index, "GENERAL");
            JsonNode report = json(mockMvc.perform(post("/api/v1/reports")
                            .header(HttpHeaders.AUTHORIZATION, bearer(reporter.accessToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(reportPayload("BALLOT", postId, "OTHER", "Evidence " + index)))
                    .andExpect(status().isCreated())
                    .andReturn());
            oldReportIds.add(UUID.fromString(report.get("id").asText()));
            Thread.sleep(5);
        }

        JsonNode firstPage = json(mockMvc.perform(get("/api/v1/reports/mine")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporter.accessToken()))
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn());

        UUID newestPost = createPost(reporter.accessToken(), "New history insert", "Arrived after cursor", "GENERAL");
        JsonNode newest = json(mockMvc.perform(post("/api/v1/reports")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporter.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportPayload("BALLOT", newestPost, "SPAM", "New report")))
                .andExpect(status().isCreated())
                .andReturn());

        JsonNode secondPage = json(mockMvc.perform(get("/api/v1/reports/mine")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporter.accessToken()))
                        .param("limit", "2")
                        .param("beforeCreatedAt", firstPage.get("nextBeforeCreatedAt").asText())
                        .param("beforeId", firstPage.get("nextBeforeId").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andReturn());

        Set<UUID> seen = new HashSet<>();
        firstPage.get("content").forEach(item -> seen.add(UUID.fromString(item.get("id").asText())));
        secondPage.get("content").forEach(item -> seen.add(UUID.fromString(item.get("id").asText())));
        assertEquals(new HashSet<>(oldReportIds), seen);
        assertFalse(seen.contains(UUID.fromString(newest.get("id").asText())));
    }

    @Test
    void resolveHidesBallotClosesReportsAndIsIdempotent() throws Exception {
        AuthSession reporter = register("case-resolve-reporter@example.com");
        AuthSession admin = admin("case-resolve-admin@example.com");
        UUID postId = createPost(reporter.accessToken(), "Resolve ballot", "Must be hidden", "GENERAL");
        UUID caseId = createReport(reporter, postId, "HARASSMENT", "Targets a private individual");

        advanceToReview(admin, caseId);
        String resolution = resolvePayload("HIDE_BALLOT", "Confirmed published policy violation", null);
        mockMvc.perform(post("/api/v1/admin/moderation-cases/{caseId}/resolve", caseId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resolution))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolutionAction").value("HIDE_BALLOT"));

        mockMvc.perform(get("/api/v1/posts/{postId}", postId))
                .andExpect(status().isNotFound());
        assertEquals("RESOLVED", scalar("select status from reports where case_id = ?", String.class, caseId));
        assertEquals(1L, auditCount(caseId, "ADMIN_RESOLVE_MODERATION_CASE"));
        assertEquals(1L, auditCount(postId, "ADMIN_HIDE_POST"));

        mockMvc.perform(post("/api/v1/admin/moderation-cases/{caseId}/resolve", caseId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resolution))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
        assertEquals(1L, auditCount(caseId, "ADMIN_RESOLVE_MODERATION_CASE"));
        assertEquals(1L, auditCount(postId, "ADMIN_HIDE_POST"));
    }

    @Test
    void concurrentResolveCallsProduceOneTargetActionAndOneCaseTransition() throws Exception {
        AuthSession reporter = register("case-concurrent-reporter@example.com");
        AuthSession admin = admin("case-concurrent-admin@example.com");
        UUID postId = createPost(reporter.accessToken(), "Concurrent case", "Resolve once", "GENERAL");
        UUID caseId = createReport(reporter, postId, "HATE_SPEECH", "Contains prohibited slurs");
        advanceToReview(admin, caseId);
        ResolveModerationCaseRequest request = new ResolveModerationCaseRequest(
                ModerationResolutionAction.HIDE_BALLOT,
                "Concurrent confirmed violation",
                null
        );
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ModerationCaseStatus>> futures = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return caseService.resolve(admin.userId(), caseId, request).status();
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(ModerationCaseStatus.RESOLVED, futures.get(0).get(15, TimeUnit.SECONDS));
            assertEquals(ModerationCaseStatus.RESOLVED, futures.get(1).get(15, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1L, auditCount(caseId, "ADMIN_RESOLVE_MODERATION_CASE"));
        assertEquals(1L, auditCount(postId, "ADMIN_HIDE_POST"));
        assertEquals("HIDDEN", scalar("select moderation_status from posts where id = ?", String.class, postId));
    }

    @Test
    void targetActionFailureRollsBackCaseResolutionAndReportClosure() throws Exception {
        AuthSession reporter = register("case-rollback-reporter@example.com");
        AuthSession admin = admin("case-rollback-admin@example.com");
        UUID postId = createPost(reporter.accessToken(), "Rollback case", "Target action conflicts", "GENERAL");
        UUID caseId = createReport(reporter, postId, "PRIVACY", "Contains private contact details");
        advanceToReview(admin, caseId);
        postModerationService.hide(admin.userId(), postId, "Independent moderation action");

        mockMvc.perform(post("/api/v1/admin/moderation-cases/{caseId}/resolve", caseId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resolvePayload("HIDE_BALLOT", "Case resolution should rollback", null)))
                .andExpect(status().isConflict());

        assertEquals("IN_REVIEW", scalar("select status from moderation_cases where id = ?", String.class, caseId));
        assertEquals("OPEN", scalar("select status from reports where case_id = ?", String.class, caseId));
        assertEquals(0L, auditCount(caseId, "ADMIN_RESOLVE_MODERATION_CASE"));
        assertEquals(1L, auditCount(postId, "ADMIN_HIDE_POST"));
    }

    @Test
    void rejectedCaseCanBeReopenedAndAcceptAnotherReportWhileCommentTargetsStayDeferred() throws Exception {
        AuthSession reporter = register("case-reopen-reporter@example.com");
        AuthSession admin = admin("case-reopen-admin@example.com");
        UUID postId = createPost(reporter.accessToken(), "Reopen case", "Second report", "GENERAL");
        UUID caseId = createReport(reporter, postId, "SPAM", "First report");
        advanceToReview(admin, caseId);

        mockMvc.perform(post("/api/v1/admin/moderation-cases/{caseId}/reject", caseId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reasonPayload("Insufficient evidence")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        mockMvc.perform(post("/api/v1/admin/moderation-cases/{caseId}/reopen", caseId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reasonPayload("New evidence was submitted")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REOPENED"));

        JsonNode second = json(mockMvc.perform(post("/api/v1/reports")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporter.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportPayload("BALLOT", postId, "MISINFORMATION", "New independent evidence")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caseStatus").value("REOPENED"))
                .andReturn());
        assertEquals(caseId.toString(), second.get("caseId").asText());
        assertEquals(2, scalar("select report_count from moderation_cases where id = ?", Integer.class, caseId));

        JsonNode deferred = json(mockMvc.perform(post("/api/v1/reports")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporter.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportPayload("COMMENT", UUID.randomUUID(), "HARASSMENT", "Comment target pending module")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetValidationStatus").value("DEFERRED"))
                .andReturn());
        UUID deferredCaseId = UUID.fromString(deferred.get("caseId").asText());
        advanceToReview(admin, deferredCaseId);
        mockMvc.perform(post("/api/v1/admin/moderation-cases/{caseId}/resolve", deferredCaseId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resolvePayload("HIDE_BALLOT", "Cannot resolve unverified comment", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Moderation target validation is deferred"));
    }

    private void advanceToReview(AuthSession admin, UUID caseId) throws Exception {
        mockMvc.perform(post("/api/v1/admin/moderation-cases/{caseId}/assign", caseId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Take ownership of moderation case\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(admin.userId().toString()));
        mockMvc.perform(post("/api/v1/admin/moderation-cases/{caseId}/triage", caseId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reasonPayload("Report has enough detail for review")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRIAGED"));
        mockMvc.perform(post("/api/v1/admin/moderation-cases/{caseId}/review", caseId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reasonPayload("Begin target and evidence review")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"));
    }

    private UUID createReport(AuthSession reporter,
                              UUID postId,
                              String reasonCode,
                              String evidence) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/reports")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporter.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportPayload("BALLOT", postId, reasonCode, evidence)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(json(result).get("caseId").asText());
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
        JsonNode payload = json(result);
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
        return json(result).get("accessToken").asText();
    }

    private UUID createPost(String token, String title, String content, String category) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PostPayload(title, content, category))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(json(result).get("id").asText());
    }

    private String reportPayload(String targetType,
                                 UUID targetId,
                                 String reasonCode,
                                 String evidenceText) throws Exception {
        return objectMapper.writeValueAsString(new ReportPayload(targetType, targetId, reasonCode, evidenceText));
    }

    private String reasonPayload(String reason) throws Exception {
        return objectMapper.writeValueAsString(new ReasonPayload(reason));
    }

    private String resolvePayload(String action, String reason, Instant until) throws Exception {
        return objectMapper.writeValueAsString(new ResolvePayload(action, reason, until));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private long count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }

    private long auditCount(UUID targetId, String action) {
        return count(
                "select count(*) from admin_audit_logs where target_id = ? and action = ?",
                targetId.toString(),
                action
        );
    }

    private <T> T scalar(String sql, Class<T> type, Object... args) {
        return jdbcTemplate.queryForObject(sql, type, args);
    }

    private record AuthSession(String accessToken, UUID userId) {
    }

    private record PostPayload(String title, String content, String category) {
    }

    private record ReportPayload(String targetType, UUID targetId, String reasonCode, String evidenceText) {
    }

    private record ReasonPayload(String reason) {
    }

    private record ResolvePayload(String action, String reason, Instant until) {
    }
}
