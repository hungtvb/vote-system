package com.hungtvb.votesystem.vote.stream;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/posts")
public class BallotVoteStreamController {
    private final BallotVoteStreamService streamService;

    public BallotVoteStreamController(BallotVoteStreamService streamService) {
        this.streamService = streamService;
    }

    @GetMapping(path = "/{postId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    ResponseEntity<SseEmitter> stream(@PathVariable UUID postId,
                                      @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Accel-Buffering", "no")
                .body(streamService.subscribe(postId, lastEventId));
    }
}
