package com.hungtvb.votesystem.comment;

import com.hungtvb.votesystem.comment.dto.CommentPageResponse;
import com.hungtvb.votesystem.comment.dto.CommentResponse;
import com.hungtvb.votesystem.comment.dto.CreateCommentRequest;
import com.hungtvb.votesystem.comment.dto.UpdateCommentRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1")
public class CommentController {
    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping("/posts/{postId}/comments")
    CommentPageResponse list(@AuthenticationPrincipal Jwt jwt,
                             @PathVariable UUID postId,
                             @RequestParam(required = false) Instant afterCreatedAt,
                             @RequestParam(required = false) UUID afterId,
                             @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return commentService.list(postId, currentUserId(jwt), afterCreatedAt, afterId, limit);
    }

    @PostMapping("/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    CommentResponse create(@AuthenticationPrincipal Jwt jwt,
                           @PathVariable UUID postId,
                           @Valid @RequestBody CreateCommentRequest request) {
        return commentService.create(UUID.fromString(jwt.getSubject()), postId, request);
    }

    @PatchMapping("/comments/{commentId}")
    CommentResponse edit(@AuthenticationPrincipal Jwt jwt,
                         @PathVariable UUID commentId,
                         @Valid @RequestBody UpdateCommentRequest request) {
        return commentService.edit(UUID.fromString(jwt.getSubject()), commentId, request);
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID commentId) {
        commentService.remove(UUID.fromString(jwt.getSubject()), commentId);
    }

    private UUID currentUserId(Jwt jwt) {
        return jwt == null ? null : UUID.fromString(jwt.getSubject());
    }
}
