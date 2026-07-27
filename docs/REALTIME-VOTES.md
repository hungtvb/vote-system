# Realtime ballot vote updates

## Endpoint

Active ballots expose a public Server-Sent Events stream:

```http
GET /api/v1/posts/{postId}/events
Accept: text/event-stream
```

The endpoint is read-only and does not require an access token. Personal vote state is intentionally excluded from the event payload.

Response headers include:

```http
Content-Type: text/event-stream
Cache-Control: no-store
X-Accel-Buffering: no
```

`X-Accel-Buffering: no` prevents compatible reverse proxies from delaying individual events. Other proxies must disable buffering for this route as well.

## Event payload

Vote changes produce a `vote-update` event after the database transaction commits:

```text
id: 2026-07-25T16:30:12.123456Z
event: vote-update
retry: 3000
data: {"postId":"...","voteScore":2,"upVotes":2,"downVotes":0,"totalVotes":2,"verdictThreshold":70,"verdict":"UP","updatedAt":"2026-07-25T16:30:12.123456Z"}
```

The payload is authoritative for shared ballot state:

- vote score;
- up/down/total counts;
- verdict threshold and current/final verdict;
- aggregate update timestamp.

It never contains `myVote`, account information, or inferred activity-feed semantics. Clients retain personal vote state from authenticated REST responses.

## Initial connection

A new connection receives:

1. a `connected` comment with a three-second reconnect recommendation;
2. the current authoritative vote snapshot as a `vote-update` event.

Only ballots that currently accept votes expose a stream. Missing ballots return `404`; closed or expired ballots return `409`.

## Reconnect and duplicate suppression

Browsers automatically send the last delivered event ID as `Last-Event-ID` when reconnecting.

- If it differs from the current ballot `updatedAt`, the server sends the latest snapshot.
- If it matches, the duplicate initial event is suppressed and the connection waits for the next committed change.

The frontend also compares `updatedAt` and ignores equal, older, malformed, or different-ballot events. This prevents delayed delivery from regressing counts.

Event history is not persisted. A reconnect converges through the current PostgreSQL snapshot instead of replaying intermediate events.

## Heartbeats and cleanup

The server sends SSE comment heartbeats at the configured interval:

```env
VOTE_STREAM_HEARTBEAT_MS=15000
VOTE_STREAM_TIMEOUT_MS=1800000
```

Emitters are removed when the response completes, times out, reports an error, or heartbeat/update delivery fails. Sends to one emitter are synchronized so heartbeat and vote events cannot corrupt stream framing.

## Transaction and async delivery boundary

PostgreSQL vote persistence and aggregate mutation remain inside the existing transaction. The aggregate write updates `posts.updated_at` together with vote counts, so the timestamp acts as both the SSE event ID and frontend monotonicity guard.

After commit:

```text
vote transaction commit
├── enqueue ranking task -> ordered ranking executor -> read latest PostgreSQL state -> Redis
└── enqueue SSE task     -> ordered SSE executor     -> connected emitters
```

Key properties:

- rolled-back transactions never enqueue ranking or SSE work;
- separate single-thread executors preserve FIFO ordering per effect;
- ranking and SSE run in parallel with each other;
- queues are bounded;
- the HTTP response returns after enqueue rather than waiting for ordinary Redis/SSE network I/O;
- failures are logged and metered without converting an already-committed vote into an HTTP failure;
- ranking tasks re-read the latest ballot row so stale concurrent snapshots cannot overwrite newer rank state or restore deleted ballots;
- shutdown/interruption/final rejection uses a safe fallback path.

Configuration:

```env
VOTE_SIDE_EFFECT_QUEUE_CAPACITY=200
VOTE_SIDE_EFFECT_SHUTDOWN_WAIT_SECONDS=10
```

Queue saturation may briefly block enqueue. This is deliberate backpressure and avoids unbounded memory growth.

Submitting the same existing vote again or removing a vote that does not exist does not emit a fake change event.

## Frontend behavior

The Ballot Edition frontend opens one `EventSource` only for the active, open full-record dialog. It does not hold one stream per feed card.

- `withCredentials: true` supports the current Vercel/Railway split.
- Native EventSource reconnection uses the server retry hint and `Last-Event-ID`.
- Stream errors do not clear REST state; the UI remains usable in REST-only mode.
- A newer stream event patches shared counts and verdict while preserving `myVote`.
- Late REST success, stale detail reload, and failed-vote rollback cannot overwrite newer SSE state.
- If a vote REST request fails, the frontend first refetches the authoritative ballot and rolls back only when no newer stream update exists.

## Observability

SSE is asynchronous servlet work. Request completion and duration are recorded when the connection actually closes, times out, or errors—not when the controller method returns.

Important metrics:

```text
vote.sse.connection.duration
vote.sse.subscribers.active
vote.http.request.duration{kind="stream",...}
http.server.requests
vote.side_effect.execution{effect="sse",result="..."}
vote.operation.stage{operation="post_commit",stage="sse",result="..."}
```

With low traffic, one long-lived SSE connection can dominate platform-level p90/p95/p99 latency. Compare route-level `kind=api` and `kind=stream` metrics before interpreting aggregate Railway latency graphs.

Logs use route templates and safe request IDs. Do not tag or log ballot IDs, user IDs, email, cookies, tokens, or token hashes.

## Production verification

For the active deployment:

```text
Frontend: https://app.ballotbox.io.vn
Backend:  https://api.ballotbox.io.vn
```

Verify:

- the browser connects directly to `https://api.ballotbox.io.vn/api/v1/posts/{postId}/events`;
- CORS allows `https://app.ballotbox.io.vn` with credentials;
- proxy buffering is disabled;
- heartbeat interval is shorter than relevant idle timeouts;
- two tabs converge after a committed vote;
- reconnect with matching `Last-Event-ID` suppresses the duplicate snapshot;
- stream failure leaves REST detail usable;
- subscriber gauge returns to baseline after dialogs close or connections fail.

## Scope boundary

The stream represents current ballot aggregates only. It is not a durable activity log, notification channel, or chat transport.

Persisted community events and notification delivery are planned separately through transactional outbox and notification work. They must not reuse this stream as if it contained durable history.