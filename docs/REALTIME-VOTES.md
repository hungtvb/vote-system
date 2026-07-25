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

`X-Accel-Buffering: no` prevents compatible reverse proxies from delaying individual events. Deployments using another proxy must disable response buffering for this route as well.

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
- up, down, and total counts;
- verdict threshold and current verdict;
- the ballot aggregate update timestamp.

It never contains `myVote`, account information, or an inferred activity-feed entry. Clients retain personal vote state from authenticated REST responses.

## Initial connection

A new connection receives:

1. a `connected` comment with a three-second reconnect recommendation;
2. the current authoritative vote snapshot as a `vote-update` event.

Only ballots that currently accept votes expose a stream. Missing ballots return `404`; closed or expired ballots return `409`.

## Reconnect and duplicate suppression

Browsers automatically send the last delivered SSE event ID as `Last-Event-ID` when reconnecting. The server compares it with the current ballot `updatedAt` event ID:

- when they differ, the server immediately sends the current snapshot;
- when they match, the server suppresses that duplicate and waits for the next committed vote change.

The frontend also compares `updatedAt` values and ignores equal, older, malformed, or different-ballot events. This prevents delayed network delivery from regressing counts.

Event history is not persisted. If a client misses one or more transient events, the next initial/reconnect snapshot converges it to the current database state rather than replaying intermediate activity.

## Heartbeats and cleanup

The server sends an SSE comment heartbeat at the configured interval. Comments keep idle proxy connections alive without changing ballot state.

```env
VOTE_STREAM_HEARTBEAT_MS=15000
VOTE_STREAM_TIMEOUT_MS=1800000
```

Emitters are removed when the response completes, times out, reports an error, or a heartbeat/update send fails. Sends to one emitter are synchronized so heartbeat and vote events cannot corrupt the stream framing.

## Transaction boundary

Vote updates are published through a transactional application event and delivered only in the `AFTER_COMMIT` phase. Rolled-back transactions never reach connected clients. Submitting the same existing vote again, or removing a vote that does not exist, does not emit a fake change event.

The aggregate update writes `posts.updated_at` in the same database statement as the vote counts. The timestamp therefore serves as both the SSE event ID and the frontend monotonicity guard.

## Frontend behavior

The Ballot Edition frontend opens one `EventSource` only for the active, open full-record dialog. This avoids holding one long-lived browser connection for every feed card.

- `withCredentials: true` supports configured cross-origin deployments.
- Native EventSource reconnection uses the server retry hint and `Last-Event-ID`.
- Stream errors do not clear or replace REST state; the UI remains fully usable in REST-only mode.
- A newer stream update patches shared counts and verdict while preserving `myVote`.
- If a vote REST request fails, the frontend first refetches the authoritative ballot. It rolls back the optimistic snapshot only when no newer stream update has arrived.

## Scope boundary

The stream represents current ballot aggregates only. It must not be presented as a durable activity log. A future activity feed requires persisted, queryable events with its own retention and privacy contract.
