package com.hungtvb.votesystem.vote;

import com.hungtvb.votesystem.common.error.ConflictException;
import com.hungtvb.votesystem.common.error.ResourceNotFoundException;
import com.hungtvb.votesystem.vote.dto.VoteRequest;
import com.hungtvb.votesystem.vote.dto.VoteResponse;
import com.hungtvb.votesystem.vote.metrics.VoteLatencyMetrics;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.hungtvb.votesystem.vote.metrics.VoteLatencyMetrics.OPERATION_CAST;
import static com.hungtvb.votesystem.vote.metrics.VoteLatencyMetrics.OPERATION_REMOVE;

@RestController
@RequestMapping("/api/v1/posts/{postId}/vote")
public class VoteController {
    private final VoteService voteService;
    private final VoteLatencyMetrics metrics;

    public VoteController(VoteService voteService, VoteLatencyMetrics metrics) {
        this.voteService = voteService;
        this.metrics = metrics;
    }

    @PutMapping
    VoteResponse cast(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID postId,
                      @Valid @RequestBody VoteRequest request) {
        VoteLatencyMetrics.VoteSample sample = metrics.startTotal(OPERATION_CAST);
        try {
            VoteResponse response = voteService.cast(UUID.fromString(jwt.getSubject()), postId, request.type());
            metrics.stopTotal(sample, "success");
            return response;
        } catch (RuntimeException exception) {
            metrics.stopTotal(sample, outcome(exception));
            throw exception;
        }
    }

    @DeleteMapping
    VoteResponse remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID postId) {
        VoteLatencyMetrics.VoteSample sample = metrics.startTotal(OPERATION_REMOVE);
        try {
            VoteResponse response = voteService.remove(UUID.fromString(jwt.getSubject()), postId);
            metrics.stopTotal(sample, "success");
            return response;
        } catch (RuntimeException exception) {
            metrics.stopTotal(sample, outcome(exception));
            throw exception;
        }
    }

    private String outcome(RuntimeException exception) {
        if (exception instanceof ConflictException) {
            return "conflict";
        }
        if (exception instanceof ResourceNotFoundException) {
            return "not_found";
        }
        return "error";
    }
}
