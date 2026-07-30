package com.hungtvb.votesystem.admin.search;

import com.hungtvb.votesystem.post.Post;
import com.hungtvb.votesystem.post.PostRepository;
import com.hungtvb.votesystem.user.AppUser;
import com.hungtvb.votesystem.user.Role;
import com.hungtvb.votesystem.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
@SpringBootTest(properties = "app.rate-limit.enabled=false")
class AdminSearchExactFilterIntegrationTests {

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

    @Autowired UserRepository userRepository;
    @Autowired PostRepository postRepository;
    @Autowired AdminUserSearchRepository adminUserSearchRepository;
    @Autowired AdminPostSearchRepository adminPostSearchRepository;

    @Test
    void exactUserRolePostAndAuthorFiltersReturnOnlyMatchingRows() {
        AppUser admin = AppUser.create("admin-search-exact-admin@example.com", "hash");
        admin.promoteToAdmin();
        admin = userRepository.saveAndFlush(admin);

        AppUser firstUser = userRepository.saveAndFlush(
                AppUser.create("admin-search-exact-first@example.com", "hash"));
        AppUser secondUser = userRepository.saveAndFlush(
                AppUser.create("admin-search-exact-second@example.com", "hash"));

        Post firstPost = postRepository.saveAndFlush(Post.create(
                firstUser.getId(),
                "Exact filter first ballot",
                "Exact filter content",
                "ADMIN_EXACT_FILTER",
                null,
                60
        ));
        postRepository.saveAndFlush(Post.create(
                secondUser.getId(),
                "Exact filter second ballot",
                "Exact filter content",
                "ADMIN_EXACT_FILTER",
                null,
                60
        ));

        var userById = adminUserSearchRepository.search(
                new AdminUserFilter(firstUser.getId(), null, null, null, null, null),
                PageRequest.of(0, 20),
                Instant.now()
        );
        assertEquals(1, userById.getTotalElements());
        assertEquals(firstUser.getId(), userById.getContent().getFirst().getId());

        var admins = adminUserSearchRepository.search(
                new AdminUserFilter(null, null, Role.ADMIN, null, null, null),
                PageRequest.of(0, 20),
                Instant.now()
        );
        assertEquals(1, admins.getTotalElements());
        assertEquals(admin.getId(), admins.getContent().getFirst().getId());

        var postById = adminPostSearchRepository.search(
                new AdminPostFilter(firstPost.getId(), null, null, null, null, null, null, null, null),
                PageRequest.of(0, 20)
        );
        assertEquals(1, postById.getTotalElements());
        assertEquals(firstPost.getId(), postById.getContent().getFirst().getId());

        var postsByAuthor = adminPostSearchRepository.search(
                new AdminPostFilter(null, null, null, firstUser.getId(), null, null, null, null, null),
                PageRequest.of(0, 20)
        );
        assertEquals(1, postsByAuthor.getTotalElements());
        assertEquals(firstUser.getId(), postsByAuthor.getContent().getFirst().getAuthorId());
    }
}
