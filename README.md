# Vote System

A production-oriented voting platform built as a **modular monolith** with a Ballot Edition user experience. PostgreSQL is the durable source of truth; Redis provides rate limiting, ranked feeds, and cache state. The Next.js frontend and Spring Boot API deploy independently.

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
│   ├── Vietnamese/English catalogs
│   ├── typed API modules
│   ├── in-memory access token
│   ├── single-request auth bootstrap
│   ├── optimistic voting
│   └── SSE reconciliation
│
└── Spring Boot 3.5 modular monolith
    ├── auth + social identity
    ├── editable/private/public profiles
    ├── admin authorization boundary
    ├── ballots/posts
    ├── votes + verdict aggregation
    ├── Redis ranking
    ├── Redis sliding-window rate limiting
    ├── SSE vote streams
    ├── observability
    ├── PostgreSQL / Supabase
    └── Redis
```

PostgreSQL owns account, identity, session, profile, ballot, and vote state. Redis is derived infrastructure: ranked feeds fall back to latest-first PostgreSQL results when Redis is unavailable, and ranking state can be rebuilt.

## Stack

### Backend

- Java 21
- Spring Boot 3.5.14
- Spring Security
- OAuth2 Resource Server and OAuth2 Client
- HS256 JWT access tokens
- rotating opaque refresh tokens in `HttpOnly` cookies
- Spring Data JPA
- PostgreSQL 17
- Spring Data Redis and Spring Cache
- Flyway migrations
- Micrometer and Spring Boot Actuator
- springdoc OpenAPI for non-production environments
- Testcontainers

### Frontend

- Next.js 16 App Router
- React 19
- TypeScript 5.9
- SCSS Modules and shared design tokens
- Ballot Edition visual system
- static export-compatible production build
- typed Vietnamese/English catalogs
- dependency-light Node tests
- deterministic headless visual and accessibility QA

## Current capabilities

### Authentication, identity, and profile

- Email/password registration and login
- Optional Google OpenID Connect and GitHub OAuth2 login
- Explicit authenticated social-account linking
- Provider discovery so disabled providers do not render dead actions
- 15-minute JWT access tokens held in browser memory only
- Rotating refresh tokens in path-scoped `HttpOnly` cookies
- SHA-256 refresh-token hashes in PostgreSQL; raw refresh tokens are never persisted
- Logout, logout-all, expiry handling, rotation, and replay detection
- Register/login/refresh bootstrap responses containing session plus private profile
- Editable display name, optional bio, one of ten Ballot Mark icons, six colors, and preferred locale
- Privacy-safe public profiles and ballot author summaries
- Public profile views hide empty bios instead of presenting placeholder copy as user content

### Internationalization

- Vietnamese and English system UI with Vietnamese as the fallback default
- Saved guest preference, supported browser-language fallback, and authenticated profile preference sync
- Domain-separated catalogs and build-time key-parity verification
- `Intl` date, number, and relative-time formatting with `vi-VN` and `en-VN`
- Manual language switching remains immediate and persisted
- Stable language-neutral backend/domain values
- User-generated ballots and bios remain in the language entered by the author

### Administrator foundation

- Application roles are restricted to `USER` and `ADMIN`
- Registration and social onboarding always create `USER`
- JWT `roles` claims map to Spring Security `ROLE_*` authorities
- `/api/v1/admin/**` is protected at request and method levels
- Controlled, disabled-by-default promotion of an existing account through environment configuration
- No bootstrap-created password, token, account, or provider identity

The audit log, moderation actions, operational APIs, and protected dashboard remain later phases of TON-109. Current boundary and bootstrap procedure: [`docs/ADMIN.md`](docs/ADMIN.md).

### Ballots and voting

- Create, browse, search, paginate, edit, close, and delete owned ballots
- Category, closing time, verdict threshold, and OPEN/CLOSED lifecycle
- Feed modes: `LATEST`, `HOT`, `TOP_DAY`, `TOP_WEEK`, and authenticated `MINE`
- Server-owned filtering by query, category, and status across the complete dataset
- Upvote, downvote, change vote, repeated-choice removal, and explicit removal
- One vote per user/ballot enforced by the database
- Atomic up/down/total counts and score updates
- Server-side projected/final verdict calculation
- Current user's vote returned without N+1 queries
- Optimistic UI updates with authoritative REST reconciliation and rollback protection

### Redis and realtime

- Atomic sliding-window Redis rate limits with `429` and `Retry-After`
- Per-IP limits for login, registration, refresh, and social-login start
- Per-user limits for ballot creation, voting, and profile update
- Redis sorted-set ranking for HOT, TOP_DAY, and TOP_WEEK
- Automatic ranking rebuild from PostgreSQL when current ranking data is empty
- Database fallback when Redis is unavailable
- Public per-ballot SSE stream for authoritative aggregate updates
- Initial snapshot, heartbeat, reconnect hint, duplicate/regressive update suppression, and emitter cleanup
- Ranking and SSE post-commit work on separate bounded FIFO executors

### Reliability and observability

- Flyway-managed schema with Hibernate `validate`
- PostgreSQL and Redis integration tests through Testcontainers
- Backend, frontend, visual QA, and production-container CI gates
- Production runtime smoke covering auth, ballot persistence/search, vote aggregation, refresh rotation, Redis, and SSE
- Request correlation through `X-Request-ID`
- Route-template HTTP latency metrics with API/stream/actuator classification
- Auth restore, rate-limit, vote transaction, post-commit, and SSE metrics
- Bounded metric tags: no user IDs, ballot IDs, emails, cookies, or tokens
- Production profile disables Swagger/OpenAPI, verbose Spring/Hibernate logging, and Hibernate statistics

## Run locally

Requirements:

- Java 21
- Maven 3.6.3+
- Node.js 22+
- npm
- Docker

Copy environment examples:

```bash
cp .env.example .env
cp frontend/.env.example frontend/.env.local
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
OpenAPI:    http://localhost:8080/v3/api-docs
Health:     http://localhost:8080/actuator/health
```

Swagger/OpenAPI is available for local/non-production use. Railway activates the `production` profile, which disables both endpoints.

The frontend uses `NEXT_PUBLIC_API_BASE_URL`. Credentialed requests send `credentials: include`; backend CORS defaults to `http://localhost:3000` locally.

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
PATCH  /api/v1/users/me
GET    /api/v1/users/{userId}

GET    /api/v1/admin/probe

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

Detailed contracts: [`docs/API.md`](docs/API.md). Admin authorization and bootstrap: [`docs/ADMIN.md`](docs/ADMIN.md).

## Authentication lifecycle

The access token is a short-lived signed JWT held in React memory. The refresh token is an opaque random value stored only in an `HttpOnly` cookie; PostgreSQL stores its hash and session metadata.

Every successful refresh rotates the token. Reusing a rotated token revokes all active refresh sessions for the affected user. An already-issued access token remains valid until its short expiry.

The public feed starts independently of session restore. In parallel, the frontend calls one `POST /api/v1/auth/refresh`; a successful response contains session and private profile. The normal path does not call `/users/me` afterward. `MINE` waits for authenticated identity, and public results are reconciled in the background so `myVote` becomes authoritative without hiding the first page.

Detailed behavior: [`docs/FRONTEND-AUTH.md`](docs/FRONTEND-AUTH.md).

## Rate-limit defaults

| Operation | Default |
|---|---:|
| Login | 5 / minute / IP |
| Register | 3 / hour / IP |
| Refresh | 20 / minute / IP |
| Social start | 20 / minute / IP |
| Create ballot | 10 / hour / user |
| Vote | 30 / minute / user |
| Profile update | 10 / hour / user |

Rate limiting is Redis-backed and fail-open by default for availability. Security-sensitive deployments can set `RATE_LIMIT_FAIL_OPEN=false` after validating Redis reliability.

## Documentation

Start at [`docs/README.md`](docs/README.md). Key documents:

- [`docs/API.md`](docs/API.md) — API, auth bootstrap, profiles, feeds, rate limits, SSE, and operations
- [`docs/FRONTEND-AUTH.md`](docs/FRONTEND-AUTH.md) — browser token/session lifecycle and rollout compatibility
- [`docs/PROFILE.md`](docs/PROFILE.md) — private/public profile and Ballot Mark contracts
- [`docs/I18N.md`](docs/I18N.md) — implemented VI/EN behavior and locale policy
- [`docs/ADMIN.md`](docs/ADMIN.md) — admin role boundary and controlled bootstrap
- [`docs/SOCIAL-LOGIN.md`](docs/SOCIAL-LOGIN.md) — Google/GitHub configuration and linking
- [`docs/REALTIME-VOTES.md`](docs/REALTIME-VOTES.md) — SSE contract and async delivery
- [`docs/DEPLOY-VERCEL-RAILWAY.md`](docs/DEPLOY-VERCEL-RAILWAY.md) — production deployment
- [`docs/DOCUMENTATION-MAINTENANCE.md`](docs/DOCUMENTATION-MAINTENANCE.md) — documentation Definition of Done
- [`DESIGN.md`](DESIGN.md) — implemented Ballot Edition UI specification

## Roadmap

Completed foundations include Next.js migration, Ballot Edition, server-owned feeds, Redis rate limiting/ranking, realtime vote updates, social login, VI/EN i18n, editable profiles, custom Ballot Marks, split deployment, runtime QA, performance instrumentation, and locale preference synchronization.

The active sequence is:

1. `TON-109` — admin foundation
   - `TON-192` — authorization boundary and controlled bootstrap
   - audit log and admin operations
   - protected admin dashboard
2. `TON-140` — unified reporting and moderation-case workflow
3. `TON-110` / `TON-111` — comments, replies, voting, and moderation
4. `TON-112` / `TON-113` / `TON-114` — notifications, transactional outbox, and realtime delivery
5. `TON-115` — constrained one-to-one text messaging after moderation/event reliability are stable
