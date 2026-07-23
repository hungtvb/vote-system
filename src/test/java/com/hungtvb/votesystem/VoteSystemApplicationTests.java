package com.hungtvb.votesystem;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class VoteSystemApplicationTests {
    private static final String REFRESH_COOKIE = "vote_refresh";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void registerCreatePostAndChangeVote() throws Exception {
        String token = register("hung-vote@example.com");
        String postId = createPost(token, "First post", "Production vote system");

        castVote(token, postId, "UP", 1);
        castVote(token, postId, "UP", 1);
        castVote(token, postId, "DOWN", -1);

        mockMvc.perform(delete("/api/v1/posts/{postId}/vote", postId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voteScore").value(0))
                .andExpect(jsonPath("$.myVote").doesNotExist());
    }

    @Test
    void onlyOwnerCanUpdateAndDeletePost() throws Exception {
        String ownerToken = register("owner@example.com");
        String otherToken = register("other@example.com");
        String postId = createPost(ownerToken, "Original title", "Original content");

        castVote(otherToken, postId, "UP", 1);

        mockMvc.perform(put("/api/v1/posts/{postId}", postId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Updated title","content":"Updated content"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated title"))
                .andExpect(jsonPath("$.content").value("Updated content"))
                .andExpect(jsonPath("$.voteScore").value(1));

        mockMvc.perform(put("/api/v1/posts/{postId}", postId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Hijacked title","content":"Should not be saved"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Only the author can modify this post"));

        mockMvc.perform(delete("/api/v1/posts/{postId}", postId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/posts/{postId}", postId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/posts/{postId}", postId))
                .andExpect(status().isNotFound());
    }

    @Test
    void rotatesRefreshTokenAndRevokesTheChainWhenAnOldTokenIsReplayed() throws Exception {
        AuthSession original = registerSession("refresh-rotation@example.com");

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(original.refreshCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.expiresInSeconds").value(900))
                .andExpect(jsonPath("$.refreshExpiresInSeconds").value(2_592_000))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andReturn();

        Cookie rotatedCookie = extractRefreshCookie(refreshResult);
        assertNotEquals(original.refreshCookie().getValue(), rotatedCookie.getValue());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(original.refreshCookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Refresh session is invalid or expired"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(rotatedCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutAndLogoutAllRevokeRefreshSessions() throws Exception {
        AuthSession first = registerSession("session-revocation@example.com");
        AuthSession second = loginSession("session-revocation@example.com");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(first.refreshCookie()))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(first.refreshCookie()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/logout-all")
                        .header("Authorization", "Bearer " + second.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(second.refreshCookie()))
                .andExpect(status().isUnauthorized());
    }

    private String register(String email) throws Exception {
        return registerSession(email).accessToken();
    }

    private AuthSession registerSession(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"strong-password\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")))
                .andReturn();
        return authSession(result);
    }

    private AuthSession loginSession(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"strong-password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return authSession(result);
    }

    private AuthSession authSession(MvcResult result) throws Exception {
        JsonNode auth = objectMapper.readTree(result.getResponse().getContentAsString());
        return new AuthSession(auth.get("accessToken").asText(), extractRefreshCookie(result));
    }

    private Cookie extractRefreshCookie(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        String prefix = REFRESH_COOKIE + "=";
        int start = setCookie.indexOf(prefix);
        int end = setCookie.indexOf(';', start);
        String value = setCookie.substring(start + prefix.length(), end < 0 ? setCookie.length() : end);
        return new Cookie(REFRESH_COOKIE, value);
    }

    private String createPost(String token, String title, String content) throws Exception {
        String postJson = mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PostPayload(title, content))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(postJson).get("id").asText();
    }

    private void castVote(String token, String postId, String type, int expectedScore) throws Exception {
        mockMvc.perform(put("/api/v1/posts/{postId}/vote", postId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"" + type + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voteScore").value(expectedScore))
                .andExpect(jsonPath("$.myVote").value(type));
    }

    private record AuthSession(String accessToken, Cookie refreshCookie) {
    }

    private record PostPayload(String title, String content) {
    }
}
