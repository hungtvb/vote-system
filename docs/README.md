# Vote System documentation

This directory documents the current `main` branch. Generated OpenAPI remains the source of truth for controller schemas; these guides explain runtime behavior, deployment, security boundaries, and operational decisions that are not obvious from individual endpoints.

## Current production topology

```text
Frontend  https://app.ballotbox.io.vn  Vercel / Next.js
Backend   https://api.ballotbox.io.vn  Railway / Spring Boot
Database  Supabase PostgreSQL Session Pooler
Redis     Managed Redis
DNS/TLS   Cloudflare and platform-managed certificates
```

Render is not an active production target. The root `Dockerfile` is retained for combined-image CI/runtime smoke and optional single-service deployments. Railway uses `Dockerfile.railway`, while Vercel builds from `frontend/`.

## Documentation map

| Document | Purpose |
|---|---|
| [`../README.md`](../README.md) | Project overview, stack, capabilities, local development, and roadmap |
| [`API.md`](API.md) | API overview, authentication, identity, feeds, rate limits, SSE, and observability endpoints |
| [`FRONTEND-AUTH.md`](FRONTEND-AUTH.md) | Access-token memory model, refresh rotation, session restore, social callback continuation, and split-origin cookies |
| [`SOCIAL-LOGIN.md`](SOCIAL-LOGIN.md) | Google OIDC, GitHub OAuth2, callback URLs, temporary OAuth session, and explicit account linking |
| [`REALTIME-VOTES.md`](REALTIME-VOTES.md) | Public ballot SSE contract, convergence rules, bounded async delivery, and metrics |
| [`DEPLOY-VERCEL-RAILWAY.md`](DEPLOY-VERCEL-RAILWAY.md) | Current production deployment and verification checklist |
| [`../DESIGN.md`](../DESIGN.md) | Ballot Edition visual and interaction specification |
| [`../design/stitch/ballot-edition/`](../design/stitch/ballot-edition/) | Approved Stitch reference artifacts and implementation notes |

## Source-of-truth order

When documentation and implementation differ, use this order:

1. Flyway migrations, controller mappings, DTOs, security configuration, and runtime code
2. Generated OpenAPI at `/v3/api-docs`
3. This documentation set
4. Historical PR descriptions and design exports

Do not infer production configuration from example defaults alone. Platform secrets and current provider settings remain outside the repository.

## Implemented system boundaries

### Durable source of truth

PostgreSQL owns:

- local users and linked provider identities;
- refresh sessions and token-rotation history;
- ballots, lifecycle metadata, counts, and verdict state;
- individual votes.

### Derived infrastructure

Redis owns rebuildable or short-lived state:

- sliding-window rate-limit buckets;
- HOT, TOP_DAY, and TOP_WEEK sorted sets;
- shared application cache entries.

Ranked feeds fall back to latest-first PostgreSQL results when Redis is unavailable. Redis ranking state can be rebuilt from the database.

### Realtime delivery

SSE is a convergence channel, not a durable event log. The current stream exposes only authoritative ballot aggregate snapshots. It excludes `myVote`, identity data, and activity-history semantics.

After a vote transaction commits, ranking and SSE work are enqueued to separate bounded FIFO executors. The HTTP request returns after enqueue instead of waiting for ordinary Redis and SSE network I/O. PostgreSQL remains authoritative, and ranking tasks re-read the latest ballot state before mutating Redis.

## Observability baseline

Actuator exposes `health`, `info`, and `metrics`. Important metrics include:

```text
http.server.requests
vote.http.request.duration
vote.sse.connection.duration
vote.sse.subscribers.active
vote.auth.restore.total
vote.auth.restore.stage
vote.rate_limit.latency
vote.operation.total
vote.operation.stage
vote.side_effect.execution
vote.ranking.operations
```

`X-Request-ID` is accepted when it matches the safe request-ID format; otherwise the backend generates one. Completion logs use route templates rather than concrete UUID paths. User IDs, ballot IDs, emails, cookies, refresh-token hashes, and raw tokens must never be added as metric tags or structured-log identifiers.

## Active roadmap

The next product sequence is managed in the Linear **Vote System** project:

```text
TON-116 i18n Vietnamese/English
    ↓
TON-108 Profile + Ballot Mark
    ↓
TON-109 Admin + audit + moderation
    ↓
TON-110/111 Comments + comment voting
    ↓
TON-112/113/114 Notifications + outbox + realtime
    ↓
TON-115 Direct messaging
```

Every feature after TON-116 must add Vietnamese and English system copy in the same PR. User-generated content remains in the language entered by its author.

## Documentation maintenance checklist

A feature PR should update the relevant document when it changes any of these contracts:

- public endpoint or DTO shape;
- authentication, cookie, OAuth, CORS, or authorization behavior;
- Flyway schema or durable/derived ownership;
- feed ordering, fallback, rate limiting, or SSE semantics;
- production topology or required environment variables;
- metric names, tag sets, request logging, or operational checks;
- Ballot Edition component or accessibility rules;
- roadmap dependencies that affect implementation order.
