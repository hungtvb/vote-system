package com.hungtvb.votesystem.vote.stream;

import com.hungtvb.votesystem.common.error.ConflictException;
import com.hungtvb.votesystem.common.error.ResourceNotFoundException;
import com.hungtvb.votesystem.post.Post;
import com.hungtvb.votesystem.post.PostRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BallotVoteStreamService {
    private static final long RECONNECT_DELAY_MILLIS = 3_000L;

    private final PostRepository postRepository;
    private final long timeoutMillis;
    private final Map<UUID, Map<UUID, StreamClient>> clientsByPost = new ConcurrentHashMap<>();

    public BallotVoteStreamService(PostRepository postRepository,
                                   @Value("${app.vote-stream.timeout-ms:1800000}") long timeoutMillis) {
        this.postRepository = postRepository;
        this.timeoutMillis = timeoutMillis;
    }

    public SseEmitter subscribe(UUID postId, String lastEventId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        if (!post.acceptsVotes(Instant.now())) {
            throw new ConflictException("Only active ballots expose a vote stream");
        }

        BallotVoteUpdate snapshot = BallotVoteUpdate.from(post);
        UUID clientId = UUID.randomUUID();
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        StreamClient client = new StreamClient(emitter);
        clientsByPost.computeIfAbsent(postId, ignored -> new ConcurrentHashMap<>())
                .put(clientId, client);

        Runnable cleanup = () -> remove(postId, clientId);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ignored -> cleanup.run());

        try {
            client.send(SseEmitter.event()
                    .comment("connected")
                    .reconnectTime(RECONNECT_DELAY_MILLIS));
            if (lastEventId != null && lastEventId.equals(snapshot.eventId())) {
                client.markDelivered(snapshot.updatedAt());
            } else {
                client.sendUpdate(snapshot);
            }
        } catch (IOException | IllegalStateException exception) {
            cleanup.run();
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    public void publish(BallotVoteUpdate update) {
        Map<UUID, StreamClient> clients = clientsByPost.get(update.postId());
        if (clients == null || clients.isEmpty()) {
            return;
        }
        clients.forEach((clientId, client) -> {
            try {
                client.sendUpdate(update);
            } catch (IOException | IllegalStateException exception) {
                remove(update.postId(), clientId);
                client.completeWithError(exception);
            }
        });
    }

    @Scheduled(fixedDelayString = "${app.vote-stream.heartbeat-ms:15000}")
    public void heartbeat() {
        clientsByPost.forEach((postId, clients) -> clients.forEach((clientId, client) -> {
            try {
                client.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException | IllegalStateException exception) {
                remove(postId, clientId);
                client.completeWithError(exception);
            }
        }));
    }

    public int activeSubscribers(UUID postId) {
        Map<UUID, StreamClient> clients = clientsByPost.get(postId);
        return clients == null ? 0 : clients.size();
    }

    private void remove(UUID postId, UUID clientId) {
        clientsByPost.computeIfPresent(postId, (ignored, clients) -> {
            clients.remove(clientId);
            return clients.isEmpty() ? null : clients;
        });
    }

    private static final class StreamClient {
        private final SseEmitter emitter;
        private Instant lastDeliveredAt;

        private StreamClient(SseEmitter emitter) {
            this.emitter = emitter;
        }

        private synchronized void markDelivered(Instant deliveredAt) {
            if (lastDeliveredAt == null || deliveredAt.isAfter(lastDeliveredAt)) {
                lastDeliveredAt = deliveredAt;
            }
        }

        private synchronized void sendUpdate(BallotVoteUpdate update) throws IOException {
            if (lastDeliveredAt != null && !update.updatedAt().isAfter(lastDeliveredAt)) {
                return;
            }
            emitter.send(SseEmitter.event()
                    .name("vote-update")
                    .id(update.eventId())
                    .reconnectTime(RECONNECT_DELAY_MILLIS)
                    .data(update));
            lastDeliveredAt = update.updatedAt();
        }

        private synchronized void send(SseEmitter.SseEventBuilder event) throws IOException {
            emitter.send(event);
        }

        private void completeWithError(Throwable error) {
            try {
                emitter.completeWithError(error);
            } catch (IllegalStateException ignored) {
                // The response can already be completed by the servlet container.
            }
        }
    }
}
