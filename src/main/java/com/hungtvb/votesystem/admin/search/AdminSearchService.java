package com.hungtvb.votesystem.admin.search;

import com.hungtvb.votesystem.admin.search.dto.AdminPageResponse;
import com.hungtvb.votesystem.admin.search.dto.AdminPostResponse;
import com.hungtvb.votesystem.admin.search.dto.AdminUserResponse;
import com.hungtvb.votesystem.auth.social.SocialProvider;
import com.hungtvb.votesystem.auth.social.UserIdentityRepository;
import com.hungtvb.votesystem.auth.social.UserProviderSummary;
import com.hungtvb.votesystem.common.error.InvalidRequestException;
import com.hungtvb.votesystem.common.error.ResourceNotFoundException;
import com.hungtvb.votesystem.post.Post;
import com.hungtvb.votesystem.post.dto.AuthorSummary;
import com.hungtvb.votesystem.user.AppUser;
import com.hungtvb.votesystem.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminSearchService {
    private final AdminUserSearchRepository adminUserRepository;
    private final AdminPostSearchRepository adminPostRepository;
    private final UserIdentityRepository identityRepository;
    private final UserRepository userRepository;

    public AdminSearchService(AdminUserSearchRepository adminUserRepository,
                              AdminPostSearchRepository adminPostRepository,
                              UserIdentityRepository identityRepository,
                              UserRepository userRepository) {
        this.adminUserRepository = adminUserRepository;
        this.adminPostRepository = adminPostRepository;
        this.identityRepository = identityRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public AdminPageResponse<AdminUserResponse> users(Pageable pageable, AdminUserFilter requestedFilter) {
        validateRange(requestedFilter.createdFrom(), requestedFilter.createdTo());
        AdminUserFilter filter = new AdminUserFilter(
                requestedFilter.id(),
                normalizeQuery(requestedFilter.query()),
                requestedFilter.role(),
                requestedFilter.accountStatus(),
                requestedFilter.createdFrom(),
                requestedFilter.createdTo()
        );
        Instant now = Instant.now();
        Page<AppUser> result = adminUserRepository.search(filter, pageable, now);
        Map<UUID, List<SocialProvider>> providers = providerMap(
                result.getContent().stream().map(AppUser::getId).toList());
        return AdminPageResponse.from(result.map(user -> AdminUserResponse.from(
                user,
                providers.getOrDefault(user.getId(), List.of()),
                now
        )));
    }

    @Transactional(readOnly = true)
    public AdminUserResponse user(UUID userId) {
        AppUser user = adminUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Map<UUID, List<SocialProvider>> providers = providerMap(List.of(userId));
        return AdminUserResponse.from(
                user,
                providers.getOrDefault(userId, List.of()),
                Instant.now()
        );
    }

    @Transactional(readOnly = true)
    public AdminPageResponse<AdminPostResponse> posts(Pageable pageable, AdminPostFilter requestedFilter) {
        validateRange(requestedFilter.createdFrom(), requestedFilter.createdTo());
        AdminPostFilter filter = new AdminPostFilter(
                requestedFilter.id(),
                normalizeUpper(requestedFilter.ballotNumber()),
                normalizeQuery(requestedFilter.query()),
                requestedFilter.authorId(),
                normalizeUpper(requestedFilter.category()),
                requestedFilter.status(),
                requestedFilter.moderationStatus(),
                requestedFilter.createdFrom(),
                requestedFilter.createdTo()
        );
        Page<Post> result = adminPostRepository.search(filter, pageable);
        Map<UUID, AuthorSummary> authors = authorMap(
                result.getContent().stream().map(Post::getAuthorId).toList());
        return AdminPageResponse.from(result.map(post -> AdminPostResponse.from(
                post,
                authors.getOrDefault(post.getAuthorId(), AuthorSummary.technical(post.getAuthorId()))
        )));
    }

    @Transactional(readOnly = true)
    public AdminPostResponse post(UUID postId) {
        Post post = adminPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        AuthorSummary author = userRepository.findById(post.getAuthorId())
                .map(AuthorSummary::from)
                .orElseGet(() -> AuthorSummary.technical(post.getAuthorId()));
        return AdminPostResponse.from(post, author);
    }

    private Map<UUID, List<SocialProvider>> providerMap(List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Set<SocialProvider>> grouped = new LinkedHashMap<>();
        for (UserProviderSummary summary : identityRepository.findProviderSummariesByUserIds(userIds)) {
            grouped.computeIfAbsent(summary.getUserId(), ignored -> EnumSet.noneOf(SocialProvider.class))
                    .add(summary.getProvider());
        }
        Map<UUID, List<SocialProvider>> result = new HashMap<>();
        grouped.forEach((userId, providers) -> result.put(userId, List.copyOf(providers)));
        return result;
    }

    private Map<UUID, AuthorSummary> authorMap(List<UUID> authorIds) {
        if (authorIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> distinctIds = new ArrayList<>(new java.util.LinkedHashSet<>(authorIds));
        Map<UUID, AuthorSummary> result = new HashMap<>();
        userRepository.findAllById(distinctIds)
                .forEach(user -> result.put(user.getId(), AuthorSummary.from(user)));
        return result;
    }

    private void validateRange(Instant createdFrom, Instant createdTo) {
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new InvalidRequestException("createdFrom must not be after createdTo");
        }
    }

    private String normalizeQuery(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private String normalizeUpper(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip().toUpperCase(Locale.ROOT);
    }
}
