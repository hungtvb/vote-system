package com.hungtvb.votesystem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = "app.rate-limit.enabled=false")
@AutoConfigureMockMvc
class PostFeedFilterIntegrationTests {
    private static final String RANKING_CATEGORY = "RANKING_TEST";

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

    @Test
    void filtersTheFullDatasetAndKeepsPaginationMetadataCorrect() throws Exception {
        String ownerA = register("filter-owner-a@example.com");
        String ownerB = register("filter-owner-b@example.com");

        createPost(ownerA, "First needle decision", "Contains the needle phrase", "TECHNOLOGY");
        createPost(ownerA, "Finance record", "Does not match", "FINANCE");
        createPost(ownerB, "Second needle decision", "Another needle phrase", "TECHNOLOGY");
        createPost(ownerB, "Literal 100% record", "Percent marker", "WORK");

        mockMvc.perform(get("/api/v1/posts")
                        .param("query", "needle")
                        .param("category", "technology")
                        .param("status", "OPEN")
                        .param("size", "1")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].category").value("TECHNOLOGY"));

        mockMvc.perform(get("/api/v1/posts")
                        .param("query", "needle")
                        .param("category", "TECHNOLOGY")
                        .param("status", "OPEN")
                        .param("size", "1")
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(get("/api/v1/posts")
                        .param("query", "%")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Literal 100% record"));
    }

    @Test
    void mineRequiresAuthenticationAndReturnsOnlyTheCurrentAuthorsBallots() throws Exception {
        String ownerA = register("mine-owner-a@example.com");
        String ownerB = register("mine-owner-b@example.com");
        String ownerAId = userId(ownerA);
        String firstA = createPost(ownerA, "Owner A first", "Mine feed", "GENERAL");
        String secondA = createPost(ownerA, "Owner A second", "Mine feed", "TECHNOLOGY");
        createPost(ownerB, "Owner B only", "Other owner", "GENERAL");

        mockMvc.perform(get("/api/v1/posts").param("feed", "MINE"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Authentication is required for the MINE feed"));

        MvcResult result = mockMvc.perform(get("/api/v1/posts")
                        .header("Authorization", "Bearer " + ownerA)
                        .param("feed", "MINE")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].authorId", everyItem(is(ownerAId))))
                .andReturn();

        JsonNode content = objectMapper.readTree(result.getResponse().getContentAsString()).get("content");
        List<String> ids = new ArrayList<>();
        content.forEach(post -> ids.add(post.get("id").asText()));
        org.junit.jupiter.api.Assertions.assertTrue(ids.containsAll(List.of(firstA, secondA)));
    }

    @Test
    void filteredHotFeedPreservesRankOrderAndFilteredTotals() throws Exception {
        String author = register("hot-filter-author@example.com");
        String voter1 = register("hot-filter-voter-1@example.com");
        String voter2 = register("hot-filter-voter-2@example.com");
        String voter3 = register("hot-filter-voter-3@example.com");
        String voter4 = register("hot-filter-voter-4@example.com");

        String highestRanked = createPost(author, "Highest ranked match", "Ranked filter", RANKING_CATEGORY);
        String middleRanked = createPost(author, "Middle ranked match", "Ranked filter", RANKING_CATEGORY);
        String lowestRanked = createPost(author, "Lowest ranked match", "Ranked filter", RANKING_CATEGORY);
        String excludedFinance = createPost(author, "Excluded finance", "Ranked filter", "FINANCE");

        vote(voter1, highestRanked); vote(voter2, highestRanked); vote(voter3, highestRanked);
        vote(voter1, middleRanked); vote(voter2, middleRanked);
        vote(voter1, lowestRanked);
        vote(voter1, excludedFinance); vote(voter2, excludedFinance); vote(voter3, excludedFinance); vote(voter4, excludedFinance);

        assertHotPage("0", highestRanked, 3, 3);
        assertHotPage("1", middleRanked, 3, 3);
        assertHotPage("2", lowestRanked, 3, 3);
    }

    @Test
    void rejectsInvalidPaginationAndOversizedSearch() throws Exception {
        mockMvc.perform(get("/api/v1/posts").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/posts").param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/posts").param("query", "x".repeat(201)))
                .andExpect(status().isBadRequest());
    }

    private void assertHotPage(String page, String expectedId, int totalElements, int totalPages) throws Exception {
        mockMvc.perform(get("/api/v1/posts")
                        .param("feed", "HOT")
                        .param("category", RANKING_CATEGORY)
                        .param("size", "1")
                        .param("page", page))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(totalElements))
                .andExpect(jsonPath("$.totalPages").value(totalPages))
                .andExpect(jsonPath("$.content[0].id").value(expectedId));
    }

    private String register(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"strong-password\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private String userId(String token) throws Exception {
        String body = mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private String createPost(String token, String title, String content, String category) throws Exception {
        String body = mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PostPayload(title, content, category))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private void vote(String token, String postId) throws Exception {
        mockMvc.perform(put("/api/v1/posts/{postId}/vote", postId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"UP\"}"))
                .andExpect(status().isOk());
    }

    private record PostPayload(String title, String content, String category) {
    }
}
