package com.hungtvb.votesystem.comment;

import com.hungtvb.votesystem.comment.dto.CommentVoteResponse;
import com.hungtvb.votesystem.vote.dto.VoteRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/comments/{commentId}/vote")
public class CommentVoteController {
    private final CommentVoteService service;

    public CommentVoteController(CommentVoteService service) {
        this.service = service;
    }

    @PutMapping
    public CommentVoteResponse cast(@AuthenticationPrincipal Jwt jwt,
                                    @PathVariable UUID commentId,
                                    @Valid @RequestBody VoteRequest request) {
        return service.cast(UUID.fromString(jwt.getSubject()), commentId, request.type());
    }

    @DeleteMapping
    public CommentVoteResponse remove(@AuthenticationPrincipal Jwt jwt,
                                      @PathVariable UUID commentId) {
        return service.remove(UUID.fromString(jwt.getSubject()), commentId);
    }
}
