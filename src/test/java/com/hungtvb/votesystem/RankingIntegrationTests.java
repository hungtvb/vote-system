package com.hungtvb.votesystem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = "app.rate-limit.enabled=false")
@AutoConfigureMockMvc
class RankingIntegrationTests {

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
    @Autowired StringRedisTemplate redisTemplate;

    @Test
    void voteChangesHotOrderAndMissingIndexIsRebuiltFromDatabase() throws Exception {
        String authorToken = register("ranking-author@example.com");
        String voterToken = register("ranking-voter@example.com");
        String firstPostId = createPost(authorToken, "First ranked post");
        String secondPostId = createPost(authorToken, "Second ranked post");

        mockMvc.perform(put("/api/v1/posts/{postId}/vote", firstPostId)
                        .header("Authorization", "Bearer " + voterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"UP\"}"))
                .andExpect(status().isOk());

        awaitFirstHotPost(firstPostId);

        redisTemplate.delete("feed:hot");

        assertFirstHotPost(firstPostId);

        mockMvc.perform(get("/api/v1/posts")
                        .param("feed", "TOP_DAY")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(firstPostId))
                .andExpect(jsonPath("$.content[1].id").value(secondPostId));
    }

    private void awaitFirstHotPost(String postId) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            MvcResult result = mockMvc.perform(get("/api/v1/posts")
                            .param("feed", "HOT")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode content = objectMapper.readTree(result.getResponse().getContentAsString()).path("content");
            if (content.isArray()
                    && !content.isEmpty()
                    && postId.equals(content.get(0).path("id").asText())) {
                return;
            }
            Thread.sleep(25);
        }
        assertFirstHotPost(postId);
    }

    private void assertFirstHotPost(String postId) throws Exception {
        mockMvc.perform(get("/api/v1/posts")
                        .param("feed", "HOT")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(postId));
    }

    private String register(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"strong-password\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private String createPost(String token, String title) throws Exception {
        String body = mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PostPayload(title, "Ranking integration test"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode post = objectMapper.readTree(body);
        return post.get("id").asText();
    }

    private record PostPayload(String title, String content) {
    }
}
