package com.hungtvb.votesystem.admin.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hungtvb.votesystem.admin.AdminBootstrapService;
import com.hungtvb.votesystem.auth.social.SocialProvider;
import com.hungtvb.votesystem.auth.social.UserIdentity;
import com.hungtvb.votesystem.auth.social.UserIdentityRepository;
import com.hungtvb.votesystem.post.Post;
import com.hungtvb.votesystem.post.PostRepository;
import com.hungtvb.votesystem.user.AccountStatus;
import com.hungtvb.votesystem.user.AppUser;
import com.hungtvb.votesystem.user.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {
        "app.rate-limit.enabled=false",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@AutoConfigureMockMvc
class AdminSearchIntegrationTests {
    private static final String PASSWORD = "strong-password";

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
    @Autowired UserIdentityRepository identityRepository;
    @Autowired PostRepository postRepository;
    @Autowired AdminBootstrapService adminBootstrapService;
    @Autowired AdminSearchController controller;
    @Autowired EntityManagerFactory entityManagerFactory;

    @Test
    void adminBoundaryRejectsAnonymousAndUser() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/posts"))
                .andExpect(status().isUnauthorized());

        RegisteredUser user = register(
                "admin-search-boundary-user@example.com",
                "Admin Search Boundary User"
        );
        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void userSearchSupportsEffectiveStatusWildcardsRangesProvidersAndPrivacy() throws Exception {
        RegisteredUser admin = admin(
                "admin-search-users-admin@example.com",
                "Admin Search Users Admin"
        );
        RegisteredUser alpha = register(
                "admin-search-alpha@example.com",
                "USER-FILTER-MARKER Alpha 100%"
        );
        RegisteredUser beta = register(
                "admin-search-beta@example.com",
                "USER-FILTER-MARKER Beta"
        );
        RegisteredUser expired = register(
                "admin-search-expired@example.com",
                "USER-FILTER-MARKER Expired"
        );

        AppUser alphaUser = userRepository.findById(alpha.userId()).orElseThrow();
        identityRepository.saveAndFlush(UserIdentity.create(
                alphaUser,
                SocialProvider.GOOGLE,
                "admin-search-google-alpha",
                "admin-search-alpha@example.com",
                true
        ));

        Instant now = Instant.now();
        jdbcTemplate.update("""
                update users
                   set account_status = 'SUSPENDED',
                       status_until = ?,
                       status_updated_at = ?
                 where id = ?
                """,
                Timestamp.from(now.plusSeconds(3600)),
                Timestamp.from(now),
                beta.userId());
        jdbcTemplate.update("""
                update users
                   set account_status = 'SUSPENDED',
                       status_until = ?,
                       status_updated_at = ?
                 where id = ?
                """,
                Timestamp.from(now.minusSeconds(60)),
                Timestamp.from(now.minusSeconds(120)),
                expired.userId());

        Instant alphaCreated = Instant.parse("2026-01-10T00:00:00Z");
        Instant betaCreated = Instant.parse("2026-02-10T00:00:00Z");
        Instant expiredCreated = Instant.parse("2026-03-10T00:00:00Z");
        updateUserCreatedAt(alpha.userId(), alphaCreated);
        updateUserCreatedAt(beta.userId(), betaCreated);
        updateUserCreatedAt(expired.userId(), expiredCreated);

        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .param("query", "%")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(alpha.userId().toString()))
                .andExpect(jsonPath("$.content[0].linkedProviders[0]").value("GOOGLE"))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.content[0].providerSubject").doesNotExist())
                .andExpect(jsonPath("$.content[0].refreshToken").doesNotExist())
                .andExpect(jsonPath("$.content[0].ipAddress").doesNotExist())
                .andExpect(jsonPath("$.content[0].userAgent").doesNotExist());

        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .param("query", "user-filter-marker")
                        .param("accountStatus", "ACTIVE")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[?(@.id == '%s')].accountStatus"
                        .formatted(alpha.userId())).value("ACTIVE"))
                .andExpect(jsonPath("$.content[?(@.id == '%s')].accountStatus"
                        .formatted(expired.userId())).value("ACTIVE"));

        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .param("query", "user-filter-marker")
                        .param("accountStatus", "SUSPENDED")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(beta.userId().toString()))
                .andExpect(jsonPath("$.content[0].statusUntil").exists());

        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .param("query", "user-filter-marker")
                        .param("createdFrom", "2026-02-01T00:00:00Z")
                        .param("createdTo", "2026-02-28T23:59:59Z")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(beta.userId().toString()));

        mockMvc.perform(get("/api/v1/admin/users/{userId}", alpha.userId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin-search-alpha@example.com"))
                .andExpect(jsonPath("$.linkedProviders[0]").value("GOOGLE"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.providerSubject").doesNotExist());
    }

    @Test
    void ballotSearchIncludesModeratedRowsWhilePublicPathsRemainRestricted() throws Exception {
        RegisteredUser admin = admin(
                "admin-search-posts-admin@example.com",
                "Admin Search Posts Admin"
        );
        RegisteredUser author = register(
                "admin-search-posts-author@example.com",
                "Admin Search Post Author"
        );

        Post literal = savePost(
                author.userId(),
                "POST-SEARCH-MARKER 100% decision",
                "Literal percent content",
                "ADMIN_LITERAL_SEARCH"
        );
        Post hidden = savePost(
                author.userId(),
                "POST-SEARCH-MARKER hidden",
                "Hidden admin content",
                "ADMIN_MOD_SEARCH"
        );
        hidden.hide(Instant.now());
        postRepository.saveAndFlush(hidden);
        Post deleted = savePost(
                author.userId(),
                "POST-SEARCH-MARKER deleted",
                "Deleted admin content",
                "ADMIN_MOD_SEARCH"
        );
        deleted.softDelete(Instant.now());
        postRepository.saveAndFlush(deleted);
        Post closed = savePost(
                author.userId(),
                "POST-SEARCH-MARKER closed",
                "Closed admin content",
                "ADMIN_CLOSED_SEARCH"
        );
        closed.close(Instant.now());
        postRepository.saveAndFlush(closed);

        mockMvc.perform(get("/api/v1/admin/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .param("query", "%")
                        .param("category", "admin_literal_search")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(literal.getId().toString()));

        mockMvc.perform(get("/api/v1/admin/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .param("query", "post-search-marker")
                        .param("moderationStatus", "HIDDEN")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(hidden.getId().toString()))
                .andExpect(jsonPath("$.content[0].author.displayName")
                        .value("Admin Search Post Author"));

        mockMvc.perform(get("/api/v1/admin/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .param("ballotNumber", deleted.getBallotNumber().toLowerCase())
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(deleted.getId().toString()));

        mockMvc.perform(get("/api/v1/admin/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .param("status", "CLOSED")
                        .param("category", "ADMIN_CLOSED_SEARCH")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(closed.getId().toString()));

        mockMvc.perform(get("/api/v1/admin/posts/{postId}", deleted.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moderationStatus").value("DELETED"))
                .andExpect(jsonPath("$.authorId").value(author.userId().toString()))
                .andExpect(jsonPath("$.myVote").doesNotExist())
                .andExpect(jsonPath("$.voters").doesNotExist())
                .andExpect(jsonPath("$.userIds").doesNotExist());

        mockMvc.perform(get("/api/v1/posts/{postId}", hidden.getId()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/posts/{postId}", deleted.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void paginationUsesCreatedAtAndIdAsDeterministicTieBreaker() throws Exception {
        RegisteredUser admin = admin(
                "admin-search-page-admin@example.com",
                "Admin Search Page Admin"
        );
        RegisteredUser author = register(
                "admin-search-page-author@example.com",
                "Admin Search Page Author"
        );
        List<Post> posts = List.of(
                savePost(author.userId(), "Admin page first", "Page tie", "ADMIN_PAGE_SEARCH"),
                savePost(author.userId(), "Admin page second", "Page tie", "ADMIN_PAGE_SEARCH"),
                savePost(author.userId(), "Admin page third", "Page tie", "ADMIN_PAGE_SEARCH")
        );
        Instant sharedCreatedAt = Instant.parse("2026-04-10T12:00:00Z");
        for (Post post : posts) {
            jdbcTemplate.update(
                    "update posts set created_at = ? where id = ?",
                    Timestamp.from(sharedCreatedAt),
                    post.getId()
            );
        }
        List<String> expected = jdbcTemplate.queryForList("""
                select cast(id as varchar)
                  from posts
                 where category = 'ADMIN_PAGE_SEARCH'
                 order by created_at desc, id desc
                """, String.class);

        List<String> actual = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            MvcResult result = mockMvc.perform(get("/api/v1/admin/posts")
                            .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                            .param("category", "ADMIN_PAGE_SEARCH")
                            .param("page", Integer.toString(page))
                            .param("size", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.totalPages").value(3))
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andReturn();
            actual.add(objectMapper.readTree(result.getResponse().getContentAsString())
                    .get("content").get(0).get("id").asText());
        }
        assertEquals(expected, actual);
    }

    @Test
    void listQueriesStayConstantWhenRowsHaveProvidersAndDistinctAuthors() throws Exception {
        RegisteredUser admin = admin(
                "admin-search-count-admin@example.com",
                "Admin Search Count Admin"
        );
        List<RegisteredUser> users = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            RegisteredUser user = register(
                    "admin-search-count-user-%d@example.com".formatted(index),
                    "QUERY-COUNT-USER %d".formatted(index)
            );
            users.add(user);
            AppUser entity = userRepository.findById(user.userId()).orElseThrow();
            identityRepository.saveAndFlush(UserIdentity.create(
                    entity,
                    index % 2 == 0 ? SocialProvider.GOOGLE : SocialProvider.GITHUB,
                    "admin-search-count-subject-" + index,
                    entity.getEmail(),
                    true
            ));
            savePost(
                    entity.getId(),
                    "QUERY-COUNT-POST %d".formatted(index),
                    "Query count content",
                    "ADMIN_QUERY_COUNT"
            );
        }

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .param("query", "query-count-user")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(6));
        long userStatements = statistics.getPrepareStatementCount();
        assertTrue(userStatements <= 4,
                "Expected account check + page + count + provider batch, got " + userStatements);

        statistics.clear();
        mockMvc.perform(get("/api/v1/admin/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .param("category", "ADMIN_QUERY_COUNT")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(6));
        long postStatements = statistics.getPrepareStatementCount();
        assertTrue(postStatements <= 4,
                "Expected account check + page + count + author batch, got " + postStatements);
    }

    @Test
    void validationAndMissingDetailsUseStableErrors() throws Exception {
        RegisteredUser admin = admin(
                "admin-search-validation-admin@example.com",
                "Admin Search Validation Admin"
        );

        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .param("query", "x".repeat(201)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/admin/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .param("createdFrom", "2026-05-02T00:00:00Z")
                        .param("createdTo", "2026-05-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("createdFrom must not be after createdTo"));
        mockMvc.perform(get("/api/v1/admin/users/{userId}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("User not found"));
        mockMvc.perform(get("/api/v1/admin/posts/{postId}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Post not found"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void methodGuardRejectsDirectUserInvocation() {
        assertThrows(AccessDeniedException.class, () -> controller.user(UUID.randomUUID()));
    }

    private RegisteredUser admin(String email, String displayName) throws Exception {
        RegisteredUser registered = register(email, displayName);
        assertTrue(adminBootstrapService.promoteExistingUser(email));
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Registration(email, PASSWORD, displayName))))
                .andExpect(status().isOk())
                .andReturn();
        return new RegisteredUser(
                registered.userId(),
                objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText()
        );
    }

    private RegisteredUser register(String email, String displayName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Registration(email, PASSWORD, displayName))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode payload = objectMapper.readTree(result.getResponse().getContentAsString());
        return new RegisteredUser(
                UUID.fromString(payload.get("profile").get("id").asText()),
                payload.get("accessToken").asText()
        );
    }

    private Post savePost(UUID authorId, String title, String content, String category) {
        return postRepository.saveAndFlush(Post.create(
                authorId,
                title,
                content,
                category,
                null,
                60
        ));
    }

    private void updateUserCreatedAt(UUID userId, Instant createdAt) {
        jdbcTemplate.update(
                "update users set created_at = ? where id = ?",
                Timestamp.from(createdAt),
                userId
        );
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record RegisteredUser(UUID userId, String accessToken) {
    }

    private record Registration(String email, String password, String displayName) {
    }
}
