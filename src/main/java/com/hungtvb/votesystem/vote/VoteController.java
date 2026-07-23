package com.hungtvb.votesystem.vote;

import com.hungtvb.votesystem.vote.dto.VoteRequest;
import com.hungtvb.votesystem.vote.dto.VoteResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/posts/{postId}/vote")
public class VoteController {
    private final VoteService voteService;

    public VoteController(VoteService voteService) {
        this.voteService = voteService;
    }

    @PutMapping
    VoteResponse cast(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID postId,
                      @Valid @RequestBody VoteRequest request) {
        return voteService.cast(UUID.fromString(jwt.getSubject()), postId, request.type());
    }

    @DeleteMapping
    VoteResponse remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID postId) {
        return voteService.remove(UUID.fromString(jwt.getSubject()), postId);
    }
}
