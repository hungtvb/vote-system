package com.hungtvb.votesystem.post;

import com.hungtvb.votesystem.post.dto.CreatePostRequest;
import com.hungtvb.votesystem.post.dto.PostResponse;
import com.hungtvb.votesystem.post.dto.UpdatePostRequest;
import com.hungtvb.votesystem.ranking.FeedType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/posts")
public class PostController {
    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PostResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreatePostRequest request) {
        return postService.create(UUID.fromString(jwt.getSubject()), request);
    }

    @PutMapping("/{postId}")
    PostResponse update(@AuthenticationPrincipal Jwt jwt,
                        @PathVariable UUID postId,
                        @Valid @RequestBody UpdatePostRequest request) {
        return postService.update(UUID.fromString(jwt.getSubject()), postId, request);
    }

    @PostMapping("/{postId}/close")
    PostResponse close(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID postId) {
        return postService.close(UUID.fromString(jwt.getSubject()), postId);
    }

    @DeleteMapping("/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID postId) {
        postService.delete(UUID.fromString(jwt.getSubject()), postId);
    }

    @GetMapping("/{postId}")
    PostResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID postId) {
        return postService.get(postId, currentUserId(jwt));
    }

    @GetMapping
    Page<PostResponse> list(@AuthenticationPrincipal Jwt jwt,
                            @RequestParam(defaultValue = "LATEST") FeedType feed,
                            @RequestParam(required = false) @Size(max = 200) String query,
                            @RequestParam(required = false) @Size(max = 50) String category,
                            @RequestParam(required = false) BallotStatus status,
                            @RequestParam(defaultValue = "0") @Min(0) int page,
                            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        UUID userId = currentUserId(jwt);
        return postService.list(
                PageRequest.of(page, size),
                userId,
                feed,
                new PostFilter(query, category, status, null));
    }

    private UUID currentUserId(Jwt jwt) {
        return jwt == null ? null : UUID.fromString(jwt.getSubject());
    }
}
