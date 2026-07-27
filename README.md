# Vote System

A production-oriented voting platform built as a **modular monolith** with a Ballot Edition user experience. PostgreSQL remains the source of truth, while Redis provides rate limiting and ranked feeds. The public frontend is built with Next.js and deployed separately from the Spring Boot API.

## Production

```text
https://app.ballotbox.io.vn  -> Vercel / Next.js frontend
https://api.ballotbox.io.vn  -> Railway / Spring Boot backend
PostgreSQL                   -> Supabase Session Pooler
Redis                        -> managed Redis
DNS/TLS                      -> Cloudflare + platform-managed certificates
```

Deployment details: [`docs/DEPLOY-VERCEL-RAILWAY.md`](docs/DEPLOY-VERCEL-RAILWAY.md).

## Architecture

```text
Browser
├── Next.js 16 App Router + React 19
│   ├── Ballot Edition UI
│   ├── typed API modules
│   ├── in-memory access token
│   ├── optimistic voting
│   └── SSE reconciliation
│
└── Spring Boot 3.5 modular monolith
    ├── auth + social identity
    ├── users
    ├── ballots/posts
    ├── votes + verdict aggregation
    ├── Redis ranking
    ├── Redis sliding-window rate limiting
    ├── SSE vote streams
    ├── observability
    ├── PostgreSQL / Supabase
    └── Redis
```

PostgreSQL owns durable account, session, ballot, and vote state. Redis is derived infrastructure: it can be rebuilt from PostgreSQL and ranked feeds degrade to latest-first database results when Redis is unavailable.

## Stack

### Backend

- Java 21
- Spring Boot 3.5.14
- Spring Security
- OAuth2 Resource Server and OAuth2 Client
- signed JWT access tokens
- rotating opaque refresh tokens in `HttpOnly` cookies
- Spring Data JPA
- PostgreSQL 17
- Spring Data Redis and Spring Cache
- Flyway migrations
- Micrometer and Spring Boot Actuator
- springdoc OpenAPI
- Testcontainers

### Frontend

- Next.js 16 App Router
- React 19
- TypeScript 5.9
- SCSS Modules and shared design tokens
- Ballot Edition visual system
- static export-compatible production build
- dependency-light Node tests
- deterministic headless visual and accessibility QA

## Current capabilities

### Authentication and identity

- Email/password registration and login
- Optional Google OpenID Connect and GitHub OAuth2 login
- Explicit authenticated social-account linking
- Provider discovery so disabled social providers do not render dead actions
- 15-minute JWT access tokens held in browser memory only
- Rotating refresh tokens in path-scoped `HttpOnly` cookies
- SHA-256 refresh-token hashes stored in PostgreSQL; raw refresh tokens are never persisted
- Logout, logout-all, expiry handling, and refresh-token replay detection
- `GET /api/v1/users/me` Voter ID profile with display name, initials, role, optional email, and linked providers
- Privacy-safe public author summaries; public ballot responses never expose account email

### Ballots and voting

- Create, browse, search, paginate, edit, close, and delete owned ballots
- Category, closing time, verdict threshold, and OPEN/CLOSED lifecycle
- Feed modes: `LATEST`, `HOT`, `TOP_DAY`, `TOP_WEEK`, and authenticated `MINE`
- Server-owned filtering by query, category, and status across the complete dataset
- Upvote, downvote, change vote, and remove vote
- One vote per user/ballot enforced by the database
- Atomic up/down/total counts and score updates
- Server-side projected/final verdict calculation
- Current user's vote returned without N+1 queries
- Optimistic UI updates with authoritative REST reconciliation and rollback protection

### Redis and realtime

- Atomic sliding-window Redis rate limits with `429` and `Retry-After`
- Per-IP limits for login, registration, refresh, and social-login start
- Per-user limits for ballot creation and voting
- Redis sorted-set ranking for HOT, TOP_DAY, and TOP_WEEK
- Automatic ranking rebuild from PostgreSQL when a current ranking key is empty
- Database fallback when Redis is unavailable
- Public per-ballot SSE stream for authoritative vote aggregates
- Initial snapshot, heartbeat, reconnect hint, duplicate/regressive update suppression, and emitter cleanup
- Ranking and SSE post-commit work runs through separate bounded FIFO executors so normal Redis/network latency does not remain on the vote HTTP request thread

### Reliability and observability

- Flyway-managed schema with Hibernate `validate`
- PostgreSQL and Redis integration tests through Testcontainers
- Backend, frontend, visual QA, and production-container CI gates
- Production runtime smoke covering auth, ballot persistence/search, vote aggregation, refresh rotation, Redis, and SSE
- Request correlation through `X-Request-ID`
- Route-template HTTP latency metrics with API/stream/actuator classification
- Auth restore, rate-limit, vote transaction, post-commit, and SSE connection metrics
- Bounded metric tags: no user IDs, ballot IDs, emails, cookies, or tokens in labels

## Run locally

Requirements:

- Java 21
- Maven 3.6.3+
- Node.js 22+
- npm
- Docker

Copy backend environment defaults when needed:

```bash
cp .env.example .env
```

Create `frontend/.env.local` for local API calls:

```env
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
```

Start PostgreSQL and Redis:

```bash
docker compose up -d postgres redis
```

Start the backend:

```bash
mvn spring-boot:run
```

Start the frontend in another terminal:

```bash
cd frontend
npm ci
npm run dev
```

Open:

```text
Frontend:   http://localhost:3000
Backend:    http://localhost:8080
Swagger UI: http://localhost:8080/swagger-ui.html
Health:     http://localhost:8080/actuator/health
```

The local frontend calls `http://localhost:8080` through `NEXT_PUBLIC_API_BASE_URL`. Backend CORS defaults to `http://localhost:3000`, and credentialed requests use `credentials: include` for the refresh cookie.

## Test and build

Backend:

```bash
mvn verify
```

Frontend:

```bash
cd frontend
npm ci
npm test
npm run build
```

Combined production image used by CI/runtime smoke:

```bash
docker build -t vote-system .
```

Backend-only Railway image:

```bash
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

Generated API documentation and detailed contracts: [`docs/API.md`](docs/API.md).

## Authentication lifecycle

The access token is a signed JWT with a default lifetime of 15 minutes. The refresh token is an opaque random value stored only in an `HttpOnly` cookie. PostgreSQL stores its hash and session metadata.

Every successful refresh rotates the token. Reusing a rotated token is treated as possible token theft and revokes all active refresh sessions for that user. Revoking a refresh session does not query the database for every bearer-authenticated request; an already-issued access token remains valid until its short expiry.

The public feed does not wait for session restore. On startup, public LATEST/HOT/TOP requests begin immediately while refresh/profile restoration runs in parallel. `MINE` remains protected until a valid session exists, and public results are reconciled after identity restoration so `myVote` becomes authoritative without hiding the already-rendered feed.

Detailed browser/session behavior: [`docs/FRONTEND-AUTH.md`](docs/FRONTEND-AUTH.md).

## Rate-limit defaults

| Operation | Default |
|---|---:|
| Login | 5 / minute / IP |
| Register | 3 / hour / IP |
| Refresh | 20 / minute / IP |
| Social start | 20 / minute / IP |
| Create ballot | 10 / hour / user |
| Vote | 30 / minute / user |

Rate limiting is Redis-backed and fail-open by default for availability. Security-sensitive deployments can set `RATE_LIMIT_FAIL_OPEN=false` after validating Redis reliability.

## Documentation

- [`docs/README.md`](docs/README.md) — documentation index and current-state guide
- [`docs/API.md`](docs/API.md) — API, filters, identity, social login, rate limits, SSE, and operational endpoints
- [`docs/FRONTEND-AUTH.md`](docs/FRONTEND-AUTH.md) — frontend token/session lifecycle
- [`docs/SOCIAL-LOGIN.md`](docs/SOCIAL-LOGIN.md) — Google/GitHub configuration and linking rules
- [`docs/REALTIME-VOTES.md`](docs/REALTIME-VOTES.md) — SSE contract and async delivery model
- [`docs/DEPLOY-VERCEL-RAILWAY.md`](docs/DEPLOY-VERCEL-RAILWAY.md) — current production deployment
- [`DESIGN.md`](DESIGN.md) — Ballot Edition UI specification

## Roadmap

Completed foundations include Next.js migration, Ballot Edition, server-owned feeds, Redis rate limiting/ranking, realtime vote updates, social login, split deployment, runtime QA, and initial performance instrumentation.

The active planned sequence in Linear is:

1. `TON-116` — Vietnamese/English internationalization foundation
2. `TON-108` — editable profile and Ballot Mark avatar presets
3. `TON-109` — admin roles, audit log, moderation, and operational dashboard
4. `TON-110` / `TON-111` — comments, replies, voting, and moderation
5. `TON-112` / `TON-113` / `TON-114` — notification persistence, transactional outbox, and realtime bell UI
6. `TON-115` — constrained one-to-one text messaging after moderation and event reliability are stable

User-generated ballots, bios, comments, and messages will remain in the language entered by the author; system UI and machine-owned copy will support Vietnamese and English.