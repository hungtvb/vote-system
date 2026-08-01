package com.hungtvb.votesystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hungtvb.votesystem.security.TokenService;
import com.hungtvb.votesystem.user.AppUser;
import com.hungtvb.votesystem.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = "app.rate-limit.enabled=false")
@AutoConfigureMockMvc
class UserProfileIntegrationTests {
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
    @Autowired UserRepository userRepository;

    private AppUser user;

    @BeforeEach
    void createUser() {
        userRepository.deleteAll();
        user = userRepository.saveAndFlush(AppUser.create(
                "profile-owner@example.com",
                "Profile Owner",
                "encoded-password"));
    }

    @Test
    void privateProfileRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicProfileExposesOnlyPublicIdentityFields() throws Exception {
        mockMvc.perform(get("/api/v1/users/{userId}", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.displayName").value("Profile Owner"))
                .andExpect(jsonPath("$.avatarIcon").value("CITIZEN"))
                .andExpect(jsonPath("$.avatarColor").value("NAVY"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.role").doesNotExist())
                .andExpect(jsonPath("$.linkedProviders").doesNotExist())
                .andExpect(jsonPath("$.preferredLocale").doesNotExist());
    }

    @Test
    void authenticatedOwnerCanUpdateLockedProfileContract() throws Exception {
        Map<String, Object> payload = Map.of(
                "displayName", "  Updated   Voter  ",
                "bio", "Builds reliable public software.",
                "avatarIcon", "BUILDER",
                "avatarColor", "INK_BLUE",
                "preferredLocale", "en"
        );

        mockMvc.perform(patch("/api/v1/users/me")
                        .with(currentUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Updated Voter"))
                .andExpect(jsonPath("$.bio").value("Builds reliable public software."))
                .andExpect(jsonPath("$.avatarIcon").value("BUILDER"))
                .andExpect(jsonPath("$.avatarColor").value("INK_BLUE"))
                .andExpect(jsonPath("$.preferredLocale").value("en"))
                .andExpect(jsonPath("$.email").value("profile-owner@example.com"));
    }

    @Test
    void updateRejectsInvalidNameAndUnknownAvatarValues() throws Exception {
        Map<String, Object> payload = Map.of(
                "displayName", "X",
                "bio", "",
                "avatarIcon", "UNKNOWN",
                "avatarColor", "NAVY",
                "preferredLocale", "vi"
        );

        mockMvc.perform(patch("/api/v1/users/me")
                        .with(currentUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isBadRequest());
    }

    private JwtRequestPostProcessor currentUserJwt() {
        return jwt().jwt(token -> token
                .subject(user.getId().toString())
                .claim(TokenService.ROLES_CLAIM, List.of(user.getRole().name()))
                .claim(TokenService.SECURITY_VERSION_CLAIM, user.getSecurityVersion()));
    }
}
