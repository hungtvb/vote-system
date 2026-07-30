package com.hungtvb.votesystem.admin.search;

import com.hungtvb.votesystem.admin.search.dto.AdminPageResponse;
import com.hungtvb.votesystem.admin.search.dto.AdminPostResponse;
import com.hungtvb.votesystem.admin.search.dto.AdminUserResponse;
import com.hungtvb.votesystem.post.BallotStatus;
import com.hungtvb.votesystem.post.ModerationStatus;
import com.hungtvb.votesystem.user.AccountStatus;
import com.hungtvb.votesystem.user.Role;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSearchController {
    private final AdminSearchService service;

    public AdminSearchController(AdminSearchService service) {
        this.service = service;
    }

    @GetMapping("/users")
    public AdminPageResponse<AdminUserResponse> users(
            @RequestParam(required = false) UUID id,
            @RequestParam(required = false) @Size(max = 200) String query,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) AccountStatus accountStatus,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.users(
                PageRequest.of(page, size),
                new AdminUserFilter(id, query, role, accountStatus, createdFrom, createdTo)
        );
    }

    @GetMapping("/users/{userId}")
    public AdminUserResponse user(@PathVariable UUID userId) {
        return service.user(userId);
    }

    @GetMapping("/posts")
    public AdminPageResponse<AdminPostResponse> posts(
            @RequestParam(required = false) UUID id,
            @RequestParam(required = false) @Size(max = 32) String ballotNumber,
            @RequestParam(required = false) @Size(max = 200) String query,
            @RequestParam(required = false) UUID authorId,
            @RequestParam(required = false) @Size(max = 50) String category,
            @RequestParam(required = false) BallotStatus status,
            @RequestParam(required = false) ModerationStatus moderationStatus,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.posts(
                PageRequest.of(page, size),
                new AdminPostFilter(
                        id,
                        ballotNumber,
                        query,
                        authorId,
                        category,
                        status,
                        moderationStatus,
                        createdFrom,
                        createdTo
                )
        );
    }

    @GetMapping("/posts/{postId}")
    public AdminPostResponse post(@PathVariable UUID postId) {
        return service.post(postId);
    }
}
