package com.hungtvb.votesystem.admin.usermoderation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hungtvb.votesystem.admin.AdminBootstrapService;
import com.hungtvb.votesystem.auth.session.RefreshSessionService;
import com.hungtvb.votesystem.common.error.UnauthorizedException;
import jakarta.servlet.http.Cookie;
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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = "app.rate-limit.enabled=false")
@AutoConfigureMockMvc
class UserRestrictionRefreshConcurrencyIntegrationTests {
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
    @Autowired AdminBootstrapService adminBootstrapService;
    @Autowired AdminUserModerationService moderationService;
    @Autowired RefreshSessionService refreshSessionService;

    @Test
    void concurrentRefreshAndSuspensionDoNotDeadlockOrLeaveAnActiveSession() throws Exception {
        RegisteredUser admin = register("restriction-refresh-admin@example.com");
        assertTrue(adminBootstrapService.promoteExistingUser("restriction-refresh-admin@example.com"));
        RegisteredUser target = register("restriction-refresh-target@example.com");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<String> refresh = executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                try {
                    refreshSessionService.rotate(target.refreshToken());
                    return "success";
                } catch (UnauthorizedException exception) {
                    return "unauthorized";
                }
            });
            Future<String> restriction = executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                moderationService.suspend(
                        admin.userId(),
                        target.userId(),
                        "Concurrent refresh restriction",
                        null
                );
                return "success";
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            List<String> outcomes = List.of(
                    refresh.get(10, TimeUnit.SECONDS),
                    restriction.get(10, TimeUnit.SECONDS)
            );

            assertEquals("success", outcomes.get(1));
            assertTrue(outcomes.get(0).equals("success") || outcomes.get(0).equals("unauthorized"));
            assertEquals("SUSPENDED", jdbcTemplate.queryForObject(
                    "select account_status from users where id = ?", String.class, target.userId()));
            assertEquals(0L, jdbcTemplate.queryForObject(
                    "select count(*) from refresh_sessions where user_id = ? and revoked_at is null",
                    Long.class,
                    target.userId()));
            assertEquals(1L, jdbcTemplate.queryForObject(
                    "select count(*) from admin_audit_logs where target_id = ? and action = 'ADMIN_SUSPEND_USER'",
                    Long.class,
                    target.userId().toString()));
        } finally {
            executor.shutdownNow();
        }
    }

    private RegisteredUser register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Registration(email, PASSWORD))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode payload = objectMapper.readTree(result.getResponse().getContentAsString());
        return new RegisteredUser(
                UUID.fromString(payload.get("profile").get("id").asText()),
                extractRefreshToken(result)
        );
    }

    private String extractRefreshToken(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertTrue(setCookie != null && setCookie.contains(REFRESH_COOKIE + "="));
        String prefix = REFRESH_COOKIE + "=";
        int start = setCookie.indexOf(prefix);
        int end = setCookie.indexOf(';', start);
        Cookie cookie = new Cookie(
                REFRESH_COOKIE,
                setCookie.substring(start + prefix.length(), end < 0 ? setCookie.length() : end)
        );
        return cookie.getValue();
    }

    private record RegisteredUser(UUID userId, String refreshToken) {
    }

    private record Registration(String email, String password) {
    }
}
