# ADR 0003: Redis sliding-window rate limiting

- **Status:** Accepted
- **Date:** 2026-07-24

## Context

Authentication and mutation endpoints need abuse protection that works across multiple application instances. In-memory counters would diverge after horizontal scaling and reset on restart.

The limiter needs atomic window maintenance and accurate retry timing without adding a new database workload.

## Decision

Use Redis sorted sets and an atomic Lua script to implement sliding-window rate limits.

- Keys use `rate:<rule>:<subject>`.
- Anonymous authentication rules are scoped by client IP.
- Authenticated post/vote mutation rules are scoped by authenticated subject.
- The script removes expired entries, counts active requests, records an allowed request, refreshes TTL, and returns retry timing.
- Rejected requests return HTTP 429 with `Retry-After` and a problem-details body.
- Failure behavior is controlled by `app.rate-limit.fail-open`.

## Consequences

### Positive

- Limits are shared across application replicas.
- Lua execution makes each decision atomic.
- Sliding windows avoid fixed-window boundary bursts.
- Redis TTL removes stale limiter data automatically.

### Negative

- Redis becomes part of the request path for protected endpoints.
- Fail-open creates an abuse-protection gap during Redis incidents.
- Fail-closed can reduce API availability.
- Client-IP identity is only reliable when trusted proxy/header handling is configured.

## Guardrails

- Never include credentials, raw tokens, or passwords in Redis keys.
- Alert on limiter errors and rejection spikes.
- Use short Redis connection and command timeouts.
- Review fail-open versus fail-closed per environment and endpoint risk.
- Treat PostgreSQL constraints and authorization as independent controls; rate limiting is not a correctness or security boundary by itself.