package com.hungtvb.votesystem.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class UserIdentityApiTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void currentVoterProfileRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void exposesPrivateCurrentProfileAndSafePublicAuthorSummary() throws Exception {
        AuthSession session = register("hung.tran@example.com", "  Hung   Tran  ");

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(session.userId()))
                .andExpect(jsonPath("$.email").value("hung.tran@example.com"))
                .andExpect(jsonPath("$.displayName").value("Hung Tran"))
                .andExpect(jsonPath("$.initials").value("HT"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.updatedAt").isString());

        MvcResult created = mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", "Bearer " + session.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Identity contract","content":"Public author data must remain safe"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorId").value(session.userId()))
                .andExpect(jsonPath("$.author.id").value(session.userId()))
                .andExpect(jsonPath("$.author.displayName").value("Hung Tran"))
                .andExpect(jsonPath("$.author.initials").value("HT"))
                .andExpect(jsonPath("$.author.email").doesNotExist())
                .andReturn();

        String postId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/v1/posts/{postId}", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author.displayName").value("Hung Tran"))
                .andExpect(jsonPath("$.author.initials").value("HT"))
                .andExpect(jsonPath("$.author.email").doesNotExist());

        mockMvc.perform(get("/api/v1/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].author.displayName", hasItem("Hung Tran")))
                .andExpect(jsonPath("$.content[*].author.initials", hasItem("HT")))
                .andExpect(jsonPath("$.content[*].author.email").doesNotExist());
    }

    @Test
    void registrationWithoutDisplayNameUsesAPseudonymInsteadOfEmailIdentity() throws Exception {
        AuthSession session = register("sensitive.login@example.com", null);

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("sensitive.login@example.com"))
                .andExpect(jsonPath("$.displayName", matchesPattern("Voter [A-F0-9]{8}")))
                .andExpect(jsonPath("$.initials", matchesPattern("V[A-F0-9]")));
    }

    private AuthSession register(String email, String displayName) throws Exception {
        String payload = displayName == null
                ? objectMapper.writeValueAsString(new Registration(email, null, "strong-password"))
                : objectMapper.writeValueAsString(new Registration(email, displayName, "strong-password"));
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return new AuthSession(response.get("accessToken").asText(), response.get("userId").asText());
    }

    private record Registration(String email, String displayName, String password) {
    }

    private record AuthSession(String accessToken, String userId) {
    }
}
