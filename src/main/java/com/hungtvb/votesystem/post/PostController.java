package com.hungtvb.votesystem.post;

import com.hungtvb.votesystem.post.dto.CreatePostRequest;
import com.hungtvb.votesystem.post.dto.PostResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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

    @GetMapping("/{postId}")
    PostResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID postId) {
        return postService.get(postId, currentUserId(jwt));
    }

    @GetMapping
    Page<PostResponse> list(@AuthenticationPrincipal Jwt jwt,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        return postService.list(PageRequest.of(Math.max(page, 0), safeSize), currentUserId(jwt));
    }

    private UUID currentUserId(Jwt jwt) {
        return jwt == null ? null : UUID.fromString(jwt.getSubject());
    }
}
