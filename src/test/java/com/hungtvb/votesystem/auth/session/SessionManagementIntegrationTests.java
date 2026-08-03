package com.hungtvb.votesystem.auth.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = "app.rate-limit.enabled=false")
@AutoConfigureMockMvc
class SessionManagementIntegrationTests {
    private static final String PASSWORD = "strong-password";
    private static final String REFRESH_COOKIE = "vote_refresh";
    private static final String WINDOWS_CHROME = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/126.0 Safari/537.36 private-id";
    private static final String IOS_SAFARI = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) Version/18.0 Mobile Safari/604.1 private-id";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired RefreshSessionService refreshSessionService;

    @Test
    void listsOnlySafeOwnedSessionsAndMarksTheCurrentFamily() throws Exception {
        AuthSession first = register("session-list-owner@example.com", WINDOWS_CHROME);
        AuthSession current = login("session-list-owner@example.com", IOS_SAFARI);
        AuthSession otherUser = register("session-list-other@example.com", "custom-sensitive-agent/987");

        JsonNode sessions = listSessions(current.accessToken());

        assertThat(sessions.size()).isEqualTo(2);
        assertThat(sessions.toString())
                .doesNotContain("token")
                .doesNotContain("hash")
                .doesNotContain("userAgent")
                .doesNotContain("ipAddress")
                .doesNotContain("private-id");
        assertThat(values(sessions, "provider")).containsExactly("PASSWORD");
        assertThat(values(sessions, "clientLabel"))
                .containsExactlyInAnyOrder("Chrome on Windows", "Safari on iOS");
        assertThat(countBoolean(sessions, "current", true)).isEqualTo(1);

        UUID otherFamily = currentFamily(listSessions(otherUser.accessToken()));
        mockMvc.perform(delete("/api/v1/auth/sessions/{sessionId}", otherFamily)
                        .header(HttpHeaders.AUTHORIZATION, bearer(current.accessToken())))
                .andExpect(status().isNotFound());
    }

    @Test
    void revokesAnotherFamilyButNeverTheCurrentFamily() throws Exception {
        AuthSession first = register("session-revoke@example.com", WINDOWS_CHROME);
        AuthSession current = login("session-revoke@example.com", IOS_SAFARI);
        JsonNode before = listSessions(current.accessToken());
        UUID currentFamily = currentFamily(before);
        UUID otherFamily = otherFamily(before, currentFamily);

        mockMvc.perform(delete("/api/v1/auth/sessions/{sessionId}", currentFamily)
                        .header(HttpHeaders.AUTHORIZATION, bearer(current.accessToken())))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/v1/auth/sessions/{sessionId}", otherFamily)
                        .header(HttpHeaders.AUTHORIZATION, bearer(current.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revokedSessions").value(1));

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(first.refreshCookie()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(current.refreshCookie()))
                .andExpect(status().isOk());
    }

    @Test
    void rotationPreservesFamilyIdentityAndCoarseMetadata() throws Exception {
        AuthSession original = register("session-rotation-metadata@example.com", WINDOWS_CHROME);
        JsonNode before = listSessions(original.accessToken());
        UUID familyId = currentFamily(before);
        String createdAt = before.get(0).get("createdAt").asText();

        MvcResult rotatedResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(original.refreshCookie()))
                .andExpect(status().isOk())
                .andReturn();
        AuthSession rotated = authSession(rotatedResult);
        JsonNode after = listSessions(rotated.accessToken());

        assertThat(after.size()).isEqualTo(1);
        assertThat(currentFamily(after)).isEqualTo(familyId);
        assertThat(after.get(0).get("createdAt").asText()).isEqualTo(createdAt);
        assertThat(after.get(0).get("provider").asText()).isEqualTo("PASSWORD");
        assertThat(after.get(0).get("clientLabel").asText()).isEqualTo("Chrome on Windows");
    }

    @Test
    void concurrentRotationAndFamilyRevocationCannotLeaveAReplacementActive() throws Exception {
        AuthSession target = register("session-race@example.com", WINDOWS_CHROME);
        AuthSession current = login("session-race@example.com", IOS_SAFARI);
        JsonNode sessions = listSessions(current.accessToken());
        UUID currentFamily = currentFamily(sessions);
        UUID targetFamily = otherFamily(sessions, currentFamily);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<String> rotate = executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                try {
                    refreshSessionService.rotate(target.refreshCookie().getValue());
                    return "rotated";
                } catch (RuntimeException exception) {
                    return "rejected";
                }
            });
            Future<Integer> revoke = executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return refreshSessionService.revokeOtherFamily(
                        current.userId(), currentFamily, targetFamily);
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertThat(rotate.get(10, TimeUnit.SECONDS)).isIn("rotated", "rejected");
            assertEquals(1, revoke.get(10, TimeUnit.SECONDS));
            assertEquals(0L, jdbcTemplate.queryForObject(
                    "select count(*) from refresh_sessions where user_id = ? and family_id = ? and revoked_at is null",
                    Long.class,
                    current.userId(),
                    targetFamily
            ));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void suspiciousReplayRevokesTheChainAndPersistsOnlySafeSecurityEventFields() throws Exception {
        AuthSession original = register("session-reuse-event@example.com", WINDOWS_CHROME);
        MvcResult rotation = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(original.refreshCookie()))
                .andExpect(status().isOk())
                .andReturn();
        AuthSession rotated = authSession(rotation);

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(original.refreshCookie()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(rotated.refreshCookie()))
                .andExpect(status().isUnauthorized());

        assertEquals(1L, jdbcTemplate.queryForObject(
                "select count(*) from account_security_events where user_id = ? and event_type = 'SUSPICIOUS_TOKEN_REUSE'",
                Long.class,
                original.userId()
        ));
        List<String> columns = jdbcTemplate.queryForList(
                """
                select column_name
                  from information_schema.columns
                 where table_schema = 'public'
                   and table_name = 'account_security_events'
                 order by column_name
                """,
                String.class
        );
        assertThat(columns).doesNotContain(
                "token", "token_hash", "refresh_token", "ip_address", "user_agent", "raw_user_agent");
    }

    private AuthSession register(String email, String userAgent) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .header("User-Agent", userAgent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Credentials(email, PASSWORD))))
                .andExpect(status().isCreated())
                .andReturn();
        return authSession(result);
    }

    private AuthSession login(String email, String userAgent) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .header("User-Agent", userAgent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Credentials(email, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return authSession(result);
    }

    private AuthSession authSession(MvcResult result) throws Exception {
        JsonNode payload = objectMapper.readTree(result.getResponse().getContentAsString());
        return new AuthSession(
                UUID.fromString(payload.get("profile").get("id").asText()),
                payload.get("accessToken").asText(),
                extractRefreshCookie(result)
        );
    }

    private JsonNode listSessions(String accessToken) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private Cookie extractRefreshCookie(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains(REFRESH_COOKIE + "=");
        String prefix = REFRESH_COOKIE + "=";
        int start = setCookie.indexOf(prefix);
        int end = setCookie.indexOf(';', start);
        return new Cookie(
                REFRESH_COOKIE,
                setCookie.substring(start + prefix.length(), end < 0 ? setCookie.length() : end)
        );
    }

    private UUID currentFamily(JsonNode sessions) {
        for (JsonNode session : sessions) {
            if (session.get("current").asBoolean()) {
                return UUID.fromString(session.get("id").asText());
            }
        }
        throw new AssertionError("Current session was not marked");
    }

    private UUID otherFamily(JsonNode sessions, UUID currentFamily) {
        for (JsonNode session : sessions) {
            UUID familyId = UUID.fromString(session.get("id").asText());
            if (!familyId.equals(currentFamily)) {
                return familyId;
            }
        }
        throw new AssertionError("Other session was not found");
    }

    private Set<String> values(JsonNode array, String field) {
        Set<String> values = new HashSet<>();
        array.forEach(node -> values.add(node.get(field).asText()));
        return values;
    }

    private long countBoolean(JsonNode array, String field, boolean value) {
        long count = 0;
        for (JsonNode node : array) {
            if (node.get(field).asBoolean() == value) {
                count++;
            }
        }
        return count;
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Credentials(String email, String password) {
    }

    private record AuthSession(UUID userId, String accessToken, Cookie refreshCookie) {
    }
}
