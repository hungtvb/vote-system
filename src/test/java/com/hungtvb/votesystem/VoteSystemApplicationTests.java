package com.hungtvb.votesystem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class VoteSystemApplicationTests {
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

    private String register(String email) throws Exception {
        String authJson = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"strong-password\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode auth = objectMapper.readTree(authJson);
        return auth.get("accessToken").asText();
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

    private record PostPayload(String title, String content) {
    }
}