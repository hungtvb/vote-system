# Vote System

Production-oriented voting platform built as a Spring Boot modular monolith with a Next.js Ballot Edition frontend.

## Production

```text
Frontend    https://app.ballotbox.io.vn  Vercel / Next.js
Backend     https://api.ballotbox.io.vn  Railway / Spring Boot
PostgreSQL  Supabase Session Pooler
Redis       Managed Redis
DNS/TLS     Cloudflare + platform certificates
```

PostgreSQL is the durable source of truth. Redis contains rebuildable rate-limit, cache, and ranked-feed state.

## Stack

**Backend:** Java 21, Spring Boot 3.5, Spring Security, OAuth2/OIDC, JWT access tokens, rotating refresh cookies, Spring Data JPA/Redis, PostgreSQL 17, Flyway, Micrometer, Testcontainers.

**Frontend:** Next.js 16 App Router, React 19, TypeScript 5.9, SCSS Modules, static-export-compatible production build, VI/EN catalogs, deterministic visual/accessibility QA.

## Implemented capabilities

### Authentication and identity

- Email/password, Google OIDC, and GitHub OAuth2.
- Explicit social-account linking; no silent email-based merge.
- Access token held in browser memory only.
- Rotating `HttpOnly` refresh cookie with replay detection.
- Single-request register/login/refresh bootstrap returning session plus private profile.
- Editable display name, bio, Ballot Mark, color, and preferred locale.
- Privacy-safe public profile and ballot-author summaries.

### Ballots and voting

- Create, browse, search, paginate, edit, close, and author-delete ballots.
- `LATEST`, `HOT`, `TOP_DAY`, `TOP_WEEK`, and authenticated `MINE` feeds.
- Upvote/downvote, change, repeated-choice removal, and explicit removal.
- Atomic aggregates, score, projected verdict, and final verdict.
- Optimistic frontend updates with authoritative REST and SSE reconciliation.

### Admin and moderation

Application roles are limited to `USER` and `ADMIN`. Every `/api/v1/admin/**` route is protected at request and method levels.

Implemented foundations:

- disabled-by-default promotion of an existing account to ADMIN;
- append-only PostgreSQL audit log with bounded JSONB metadata;
- database trigger rejecting audit updates and deletes;
- protected paginated audit-log read API;
- audited ballot hide, restore, and administrator soft-delete.

Ballot lifecycle and moderation are separate:

```text
BallotStatus       OPEN | CLOSED
ModerationStatus   VISIBLE | HIDDEN | DELETED
```

Hidden/deleted ballots are excluded from public feeds, detail, voting, SSE, and author mutations. Administrator soft-delete preserves ballot and vote rows. PostgreSQL state and the matching audit record commit atomically under a ballot lock; Redis/SSE convergence runs after commit.

### Redis and realtime

- Atomic Redis sliding-window rate limits.
- Redis sorted-set ranking with PostgreSQL fallback.
- Ranked IDs re-checked against PostgreSQL visibility, so stale Redis members cannot expose moderated ballots.
- Public per-ballot SSE snapshots, heartbeats, reconnect hints, and cleanup.
- Ranking and SSE side effects use separate bounded FIFO executors.

### Reliability

- Flyway schema with Hibernate `validate`.
- PostgreSQL and Redis Testcontainers integration tests.
- Backend, frontend, visual/accessibility, production-image, and runtime-smoke CI gates.
- `X-Request-ID`, bounded metric tags, and production-safe logging.
- Swagger/OpenAPI available locally and disabled by the Railway production profile.

## Run locally

Requirements: Java 21, Maven 3.6.3+, Node.js 22+, npm, Docker.

```bash
cp .env.example .env
cp frontend/.env.example frontend/.env.local
docker compose up -d postgres redis
mvn spring-boot:run
```

In another terminal:

```bash
cd frontend
npm ci
npm run dev
```

```text
Frontend    http://localhost:3000
Backend     http://localhost:8080
Swagger UI  http://localhost:8080/swagger-ui.html
OpenAPI     http://localhost:8080/v3/api-docs
Health      http://localhost:8080/actuator/health
```

## Test and build

```bash
mvn verify

cd frontend
npm ci
npm test
npm run build
```

Production images:

```bash
docker build -t vote-system .
docker build -f Dockerfile.railway -t vote-system-api .
```

## API overview

```http
POST   /api/v1/auth/register
POST   /api/v1/auth/login
POST   /api/v1/auth/refresh
POST   /api/v1/auth/logout
POST   /api/v1/auth/logout-all

GET    /api/v1/auth/social/providers
POST   /api/v1/auth/social/{provider}/start
POST   /api/v1/auth/social/{provider}/link/start

GET    /api/v1/users/me
PATCH  /api/v1/users/me
GET    /api/v1/users/{userId}

GET    /api/v1/admin/probe
GET    /api/v1/admin/audit-logs
POST   /api/v1/admin/posts/{postId}/hide
POST   /api/v1/admin/posts/{postId}/restore
POST   /api/v1/admin/posts/{postId}/delete

GET    /api/v1/posts
POST   /api/v1/posts
GET    /api/v1/posts/{postId}
PUT    /api/v1/posts/{postId}
POST   /api/v1/posts/{postId}/close
DELETE /api/v1/posts/{postId}

PUT    /api/v1/posts/{postId}/vote
DELETE /api/v1/posts/{postId}/vote
GET    /api/v1/posts/{postId}/events
```

## Documentation

Start at [`docs/README.md`](docs/README.md).

- [`docs/API.md`](docs/API.md) — API contracts and runtime boundaries.
- [`docs/ADMIN.md`](docs/ADMIN.md) — authorization, bootstrap, audit, and admin mutations.
- [`docs/MODERATION.md`](docs/MODERATION.md) — ballot moderation, visibility, Redis/SSE, and concurrency.
- [`docs/FRONTEND-AUTH.md`](docs/FRONTEND-AUTH.md) — browser session lifecycle.
- [`docs/PROFILE.md`](docs/PROFILE.md) — profile and Ballot Mark contracts.
- [`docs/I18N.md`](docs/I18N.md) — Vietnamese/English behavior.
- [`docs/SOCIAL-LOGIN.md`](docs/SOCIAL-LOGIN.md) — provider setup and account linking.
- [`docs/REALTIME-VOTES.md`](docs/REALTIME-VOTES.md) — SSE and post-commit delivery.
- [`docs/DEPLOY-VERCEL-RAILWAY.md`](docs/DEPLOY-VERCEL-RAILWAY.md) — production deployment.

## Active roadmap

```text
TON-195  Audited ballot moderation and visibility enforcement
   ↓
TON-196  User suspension, banning, and access enforcement
   ↓
TON-197  Admin user and ballot search APIs
TON-198  Audited atomic ranking rebuild
   ↓
TON-199  Protected admin moderation workspace
   ↓
TON-140  Unified reports and moderation cases
```
