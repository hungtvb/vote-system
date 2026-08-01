package com.hungtvb.votesystem.admin.usermoderation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hungtvb.votesystem.admin.AdminBootstrapService;
import com.hungtvb.votesystem.admin.usermoderation.dto.AdminUserReasonRequest;
import com.hungtvb.votesystem.common.error.ConflictException;
import com.hungtvb.votesystem.user.AccountStatus;
import com.hungtvb.votesystem.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = "app.rate-limit.enabled=false")
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminUserModerationIntegrationTests {
    private static final String PASSWORD = "strong-password";
    private static final String REFRESH_COOKIE = "vote_refresh";

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
    @Autowired UserRepository userRepository;
    @Autowired AdminBootstrapService adminBootstrapService;
    @Autowired AdminUserModerationService moderationService;
    @Autowired AdminUserModerationController moderationController;

    @Test
    @Order(1)
    void concurrentCrossSuspensionLeavesOneEffectiveActiveAdministrator() throws Exception {
        AuthSession first = admin("user-moderation-concurrent-a@example.com");
        AuthSession second = admin("user-moderation-concurrent-b@example.com");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<String>> futures = new ArrayList<>();
            futures.add(executor.submit(() -> restrictConcurrently(
                    ready, start, first.userId(), second.userId(), "Concurrent safeguard A")));
            futures.add(executor.submit(() -> restrictConcurrently(
                    ready, start, second.userId(), first.userId(), "Concurrent safeguard B")));

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            List<String> outcomes = List.of(
                    futures.get(0).get(10, TimeUnit.SECONDS),
                    futures.get(1).get(10, TimeUnit.SECONDS));

            assertEquals(1, outcomes.stream().filter("success"::equals).count());
            assertEquals(1, outcomes.stream().filter("conflict"::equals).count());
            assertEquals(1L, userRepository.countEffectiveActiveAdmins(Instant.now()));
            assertEquals(1L, auditCount(null, "ADMIN_SUSPEND_USER"));

            UUID remainingAdminId = effectiveStatus(first.userId()) == AccountStatus.ACTIVE
                    ? first.userId()
                    : second.userId();
            assertThrows(ConflictException.class, () -> moderationService.suspend(
                    UUID.randomUUID(), remainingAdminId, "Last administrator guard", null));
            assertEquals(AccountStatus.ACTIVE, effectiveStatus(remainingAdminId));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Order(2)
    void adminBoundaryRejectsAnonymousAndUser() throws Exception {
        UUID targetId = UUID.randomUUID();
        String body = restriction("Published account policy violation", null);

        mockMvc.perform(post("/api/v1/admin/users/{userId}/suspend", targetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        AuthSession user = register("user-moderation-boundary@example.com");
        mockMvc.perform(post("/api/v1/admin/users/{userId}/suspend", targetId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    void suspensionImmediatelyBlocksJwtLoginRefreshWritesAndSseThenRestoreReenablesAccess() throws Exception {
        AuthSession admin = admin("user-moderation-suspend-admin@example.com");
        AuthSession target = register("user-moderation-suspend-target@example.com");
        UUID postId = createPost(target.accessToken(), "Restriction enforcement ballot");
        Instant until = Instant.now().plusSeconds(3600);

        mockMvc.perform(post("/api/v1/admin/users/{userId}/suspend", target.userId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(restriction("Repeated abuse after warning", until)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(target.userId().toString()))
                .andExpect(jsonPath("$.accountStatus").value("SUSPENDED"))
                .andExpect(jsonPath("$.statusUntil").exists())
                .andExpect(jsonPath("$.revokedSessions").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(target.accessToken())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(target.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postPayload("Blocked write")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/posts/{postId}/events", postId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(target.accessToken())))
                .andExpect(status().isUnauthorized());
        login("user-moderation-suspend-target@example.com", status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(target.refreshCookie()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/users/{userId}", target.userId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(target.userId().toString()));
        assertEquals(0L, activeRefreshSessions(target.userId()));
        assertEquals(1L, auditCount(target.userId(), "ADMIN_SUSPEND_USER"));

        mockMvc.perform(post("/api/v1/admin/users/{userId}/restore", target.userId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reason("Appeal accepted")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.statusUntil").doesNotExist());

        login("user-moderation-suspend-target@example.com", status().isOk())
                .andExpect(jsonPath("$.profile.role").value("USER"));
        assertEquals(1L, auditCount(target.userId(), "ADMIN_RESTORE_USER"));
    }

    @Test
    @Order(4)
    void permanentBanIsTerminalUntilExplicitRestoreAndRepeatedTransitionDoesNotDuplicateAudit() throws Exception {
        AuthSession admin = admin("user-moderation-ban-admin@example.com");
        AuthSession target = register("user-moderation-ban-target@example.com");

        moderate(admin.accessToken(), target.userId(), "ban", "Confirmed severe abuse", null, "BANNED");
        mockMvc.perform(post("/api/v1/admin/users/{userId}/ban", target.userId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(restriction("Repeated ban", null)))
                .andExpect(status().isConflict());

        assertEquals(1L, auditCount(target.userId(), "ADMIN_BAN_USER"));
        assertEquals("BANNED", rawStatus(target.userId()));
        assertNull(statusUntil(target.userId()));

        mockMvc.perform(post("/api/v1/admin/users/{userId}/restore", target.userId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reason("Manual administrator restore")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"));
    }

    @Test
    @Order(5)
    void expiredRestrictionIsTreatedAsActiveWithoutScheduler() throws Exception {
        AuthSession target = register("user-moderation-expired-target@example.com");
        Instant now = Instant.now();
        jdbcTemplate.update("""
                update users
                   set account_status = 'SUSPENDED',
                       status_until = ?,
                       status_updated_at = ?
                 where id = ?
                """,
                Timestamp.from(now.minusSeconds(60)),
                Timestamp.from(now.minusSeconds(120)),
                target.userId());

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(target.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(target.userId().toString()));
        login("user-moderation-expired-target@example.com", status().isOk());
        assertEquals(AccountStatus.ACTIVE, effectiveStatus(target.userId()));
    }

    @Test
    @Order(6)
    void administratorCannotModerateOrRevokeOwnCurrentAccount() throws Exception {
        AuthSession admin = admin("user-moderation-self-admin@example.com");

        for (String action : List.of("suspend", "ban")) {
            mockMvc.perform(post("/api/v1/admin/users/{userId}/{action}", admin.userId(), action)
                            .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(restriction("Self lockout attempt", null)))
                    .andExpect(status().isConflict());
        }
        mockMvc.perform(post("/api/v1/admin/users/{userId}/revoke-sessions", admin.userId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reason("Self session revocation attempt")))
                .andExpect(status().isConflict());

        assertEquals(AccountStatus.ACTIVE, effectiveStatus(admin.userId()));
        assertEquals(0L, auditCount(admin.userId(), "ADMIN_SUSPEND_USER"));
        assertEquals(0L, auditCount(admin.userId(), "ADMIN_BAN_USER"));
        assertEquals(0L, auditCount(admin.userId(), "ADMIN_REVOKE_SESSIONS"));
    }

    @Test
    @Order(7)
    void explicitSessionRevocationInvalidatesAccessJwtAndIsAudited() throws Exception {
        AuthSession admin = admin("user-moderation-revoke-admin@example.com");
        AuthSession target = register("user-moderation-revoke-target@example.com");

        mockMvc.perform(post("/api/v1/admin/users/{userId}/revoke-sessions", target.userId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reason("Security reset requested")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.revokedSessions").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(target.accessToken())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(target.refreshCookie()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/admin/users/{userId}/revoke-sessions", target.userId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reason("Repeated security reset")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revokedSessions").value(0));

        assertEquals(2L, auditCount(target.userId(), "ADMIN_REVOKE_SESSIONS"));
    }

    @Test
    @Order(8)
    void auditValidationFailureRollsBackRestrictionAndSessionRevocation() throws Exception {
        AuthSession admin = admin("user-moderation-rollback-admin@example.com");
        AuthSession target = register("user-moderation-rollback-target@example.com");
        long activeSessionsBefore = activeRefreshSessions(target.userId());

        assertThrows(IllegalArgumentException.class, () -> moderationService.suspend(
                admin.userId(), target.userId(), "x".repeat(501), null));

        assertEquals("ACTIVE", rawStatus(target.userId()));
        assertEquals(activeSessionsBefore, activeRefreshSessions(target.userId()));
        assertEquals(0L, auditCount(target.userId(), "ADMIN_SUSPEND_USER"));
        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(target.accessToken())))
                .andExpect(status().isOk());
    }

    @Test
    @Order(9)
    void invalidTemporaryRestrictionExpiryIsRejectedWithoutMutation() throws Exception {
        AuthSession admin = admin("user-moderation-expiry-admin@example.com");
        AuthSession target = register("user-moderation-expiry-target@example.com");

        assertThrows(IllegalArgumentException.class, () -> moderationService.suspend(
                admin.userId(), target.userId(), "Past expiry", Instant.now().minusSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> moderationService.ban(
                admin.userId(), target.userId(), "Excessive expiry",
                Instant.now().plus(AdminUserModerationService.MAX_RESTRICTION_DURATION).plusSeconds(60)));

        assertEquals("ACTIVE", rawStatus(target.userId()));
        assertEquals(0L, auditCount(target.userId(), "ADMIN_SUSPEND_USER"));
        assertEquals(0L, auditCount(target.userId(), "ADMIN_BAN_USER"));
    }

    @Test
    @Order(10)
    @WithMockUser(roles = "USER")
    void methodGuardRejectsDirectUserInvocation() {
        assertThrows(AccessDeniedException.class, () -> moderationController.restore(
                null,
                UUID.randomUUID(),
                new AdminUserReasonRequest("Restore reason")
        ));
    }

    private String restrictConcurrently(CountDownLatch ready,
                                        CountDownLatch start,
                                        UUID actorId,
                                        UUID targetId,
                                        String reason) throws Exception {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        try {
            moderationService.suspend(actorId, targetId, reason, null);
            return "success";
        } catch (ConflictException exception) {
            return "conflict";
        }
    }

    private AuthSession admin(String email) throws Exception {
        AuthSession registered = register(email);
        assertTrue(adminBootstrapService.promoteExistingUser(email));
        MvcResult login = login(email, status().isOk()).andReturn();
        JsonNode payload = objectMapper.readTree(login.getResponse().getContentAsString());
        return new AuthSession(
                payload.get("accessToken").asText(),
                registered.userId(),
                extractRefreshCookie(login)
        );
    }

    private AuthSession register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Registration(email, PASSWORD))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode payload = objectMapper.readTree(result.getResponse().getContentAsString());
        return new AuthSession(
                payload.get("accessToken").asText(),
                UUID.fromString(payload.get("profile").get("id").asText()),
                extractRefreshCookie(result)
        );
    }

    private org.springframework.test.web.servlet.ResultActions login(
            String email,
            org.springframework.test.web.servlet.ResultMatcher expectedStatus) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Registration(email, PASSWORD))))
                .andExpect(expectedStatus);
    }

    private UUID createPost(String token, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postPayload(title)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private void moderate(String token,
                          UUID userId,
                          String action,
                          String reason,
                          Instant until,
                          String expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/{userId}/{action}", userId, action)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(restriction(reason, until)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value(expectedStatus));
    }

    private String restriction(String reason, Instant until) throws Exception {
        return objectMapper.writeValueAsString(new Restriction(reason, until));
    }

    private String reason(String reason) throws Exception {
        return objectMapper.writeValueAsString(new AdminUserReasonRequest(reason));
    }

    private String postPayload(String title) throws Exception {
        return objectMapper.writeValueAsString(new PostPayload(title, "Account moderation integration test", "GENERAL"));
    }

    private Cookie extractRefreshCookie(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertTrue(setCookie != null && setCookie.contains(REFRESH_COOKIE + "="));
        String prefix = REFRESH_COOKIE + "=";
        int start = setCookie.indexOf(prefix);
        int end = setCookie.indexOf(';', start);
        return new Cookie(REFRESH_COOKIE,
                setCookie.substring(start + prefix.length(), end < 0 ? setCookie.length() : end));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private long activeRefreshSessions(UUID userId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from refresh_sessions where user_id = ? and revoked_at is null",
                Long.class,
                userId
        );
    }

    private long auditCount(UUID userId, String action) {
        if (userId == null) {
            return jdbcTemplate.queryForObject(
                    "select count(*) from admin_audit_logs where action = ?",
                    Long.class,
                    action
            );
        }
        return jdbcTemplate.queryForObject(
                "select count(*) from admin_audit_logs where target_id = ? and action = ?",
                Long.class,
                userId.toString(),
                action
        );
    }

    private String rawStatus(UUID userId) {
        return jdbcTemplate.queryForObject(
                "select account_status from users where id = ?",
                String.class,
                userId
        );
    }

    private Instant statusUntil(UUID userId) {
        return jdbcTemplate.queryForObject(
                "select status_until from users where id = ?",
                Instant.class,
                userId
        );
    }

    private AccountStatus effectiveStatus(UUID userId) {
        return userRepository.findById(userId).orElseThrow().effectiveAccountStatus(Instant.now());
    }

    private record AuthSession(String accessToken, UUID userId, Cookie refreshCookie) {
    }

    private record Registration(String email, String password) {
    }

    private record Restriction(String reason, Instant until) {
    }

    private record PostPayload(String title, String content, String category) {
    }
}
