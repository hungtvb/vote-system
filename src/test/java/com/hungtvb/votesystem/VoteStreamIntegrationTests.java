package com.hungtvb.votesystem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hungtvb.votesystem.vote.stream.BallotVoteStreamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.rate-limit.enabled=false",
                "app.vote-stream.heartbeat-ms=100",
                "app.vote-stream.timeout-ms=10000"
        })
@AutoConfigureMockMvc
class VoteStreamIntegrationTests {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureInfrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.url", () -> "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
    }

    @LocalServerPort int port;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired BallotVoteStreamService streamService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Test
    void twoClientsConvergeAndReconnectSkipsTheLastDeliveredSnapshot() throws Exception {
        String authorToken = register("stream-author@example.com");
        String firstVoterToken = register("stream-voter-one@example.com");
        String secondVoterToken = register("stream-voter-two@example.com");
        String postId = createPost(authorToken, "Realtime convergence ballot");
        UUID postUuid = UUID.fromString(postId);

        try (SseConnection firstClient = connect(postId, null);
             SseConnection secondClient = connect(postId, null)) {
            SseEvent firstInitial = firstClient.readDataEvent();
            SseEvent secondInitial = secondClient.readDataEvent();
            assertEquals(firstInitial.id(), secondInitial.id());
            assertVoteUpdate(firstInitial, postId, 0, 0, 0, "UNDECIDED");
            assertFalse(firstInitial.data().has("myVote"));
            awaitSubscriberCount(postUuid, 2);

            vote(firstVoterToken, postId, "UP");
            SseEvent firstUpdate = firstClient.readDataEvent();
            SseEvent secondUpdate = secondClient.readDataEvent();
            assertEquals(firstUpdate.id(), secondUpdate.id());
            assertVoteUpdate(firstUpdate, postId, 1, 0, 1, "UP");
            assertNotEquals(firstInitial.id(), firstUpdate.id());

            secondClient.close();
            awaitSubscriberCount(postUuid, 1);

            try (SseConnection reconnectedClient = connect(postId, firstUpdate.id())) {
                awaitSubscriberCount(postUuid, 2);
                Thread.sleep(5);
                vote(secondVoterToken, postId, "UP");

                SseEvent firstSecondUpdate = firstClient.readDataEvent();
                SseEvent reconnectedSecondUpdate = reconnectedClient.readDataEvent();
                assertEquals(firstSecondUpdate.id(), reconnectedSecondUpdate.id());
                assertNotEquals(firstUpdate.id(), reconnectedSecondUpdate.id());
                assertVoteUpdate(reconnectedSecondUpdate, postId, 2, 0, 2, "UP");
            }
        }

        awaitSubscriberCount(postUuid, 0);
    }

    private SseConnection connect(String postId, String lastEventId) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/posts/" + postId + "/events"))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE)
                .GET();
        if (lastEventId != null) {
            request.header("Last-Event-ID", lastEventId);
        }
        HttpResponse<InputStream> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("")
                .startsWith(MediaType.TEXT_EVENT_STREAM_VALUE));
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""));
        assertEquals("no", response.headers().firstValue("X-Accel-Buffering").orElse(""));
        return new SseConnection(response.body(), objectMapper);
    }

    private void assertVoteUpdate(SseEvent event, String postId,
                                  long upVotes, long downVotes, long totalVotes, String verdict) {
        assertEquals("vote-update", event.name());
        assertEquals(postId, event.data().get("postId").asText());
        assertEquals(upVotes, event.data().get("upVotes").asLong());
        assertEquals(downVotes, event.data().get("downVotes").asLong());
        assertEquals(totalVotes, event.data().get("totalVotes").asLong());
        assertEquals(verdict, event.data().get("verdict").asText());
        assertEquals(event.id(), event.data().get("updatedAt").asText());
    }

    private void awaitSubscriberCount(UUID postId, int expected) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (streamService.activeSubscribers(postId) == expected) {
                return;
            }
            Thread.sleep(25);
        }
        assertEquals(expected, streamService.activeSubscribers(postId));
    }

    private String register(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"strong-password\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private String createPost(String token, String title) throws Exception {
        String payload = objectMapper.writeValueAsString(new PostPayload(
                title, "Realtime vote stream integration test", "GENERAL"));
        String body = mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private void vote(String token, String postId, String type) throws Exception {
        mockMvc.perform(put("/api/v1/posts/{postId}/vote", postId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"" + type + "\"}"))
                .andExpect(status().isOk());
    }

    private record PostPayload(String title, String content, String category) {
    }

    private record SseEvent(String id, String name, JsonNode data) {
    }

    private static final class SseConnection implements Closeable {
        private final InputStream input;
        private final BufferedReader reader;
        private final ObjectMapper objectMapper;
        private boolean closed;

        private SseConnection(InputStream input, ObjectMapper objectMapper) {
            this.input = input;
            this.reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            this.objectMapper = objectMapper;
        }

        private SseEvent readDataEvent() throws Exception {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return readNextDataEvent();
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            }).get(5, TimeUnit.SECONDS);
        }

        private SseEvent readNextDataEvent() throws IOException {
            String id = null;
            String name = null;
            StringBuilder data = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (!data.isEmpty()) {
                        return new SseEvent(id, name, objectMapper.readTree(data.toString()));
                    }
                    id = null;
                    name = null;
                    continue;
                }
                if (line.startsWith(":")) {
                    continue;
                }
                if (line.startsWith("id:")) {
                    id = line.substring(3).trim();
                } else if (line.startsWith("event:")) {
                    name = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    if (!data.isEmpty()) {
                        data.append('\n');
                    }
                    data.append(line.substring(5).trim());
                }
            }
            throw new IOException("SSE connection closed before a data event was received");
        }

        @Override
        public void close() throws IOException {
            if (closed) return;
            closed = true;
            reader.close();
            input.close();
        }
    }
}
