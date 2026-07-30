package com.hungtvb.votesystem.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hungtvb.votesystem.user.AppUser;
import com.hungtvb.votesystem.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AdminAuthorizationIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired AdminProbeController adminProbeController;

    @Test
    void adminProbeRequiresAdminRoleFromIssuedJwt() throws Exception {
        mockMvc.perform(get("/api/v1/admin/probe"))
                .andExpect(status().isUnauthorized());

        String email = "admin-boundary@example.com";
        String userToken = register(email);

        mockMvc.perform(get("/api/v1/admin/probe")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        AppUser user = userRepository.findByEmail(email).orElseThrow();
        user.promoteToAdmin();
        userRepository.saveAndFlush(user);

        String adminToken = login(email);
        mockMvc.perform(get("/api/v1/admin/probe")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void methodGuardRejectsUserWhenControllerBeanIsInvokedDirectly() {
        assertThrows(AccessDeniedException.class, adminProbeController::probe);
    }

    private String register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"strong-password\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.profile.role").value("USER"))
                .andReturn();
        return accessToken(result);
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"strong-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.role").value("ADMIN"))
                .andReturn();
        return accessToken(result);
    }

    private String accessToken(MvcResult result) throws Exception {
        JsonNode payload = objectMapper.readTree(result.getResponse().getContentAsString());
        return payload.get("accessToken").asText();
    }
}
