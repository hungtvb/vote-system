# Vote System documentation

This directory documents the current branch. Controller mappings, DTOs, Flyway migrations, security configuration, runtime source, and active Spring profiles remain the implementation source of truth.

## Current production topology

```text
Frontend  https://app.ballotbox.io.vn  Vercel / Next.js
Backend   https://api.ballotbox.io.vn  Railway / Spring Boot
Database  Supabase PostgreSQL Session Pooler
Redis     Managed Redis
DNS/TLS   Cloudflare and platform-managed certificates
```

Render is not an active production target. The root `Dockerfile` is retained for combined-image CI/runtime smoke and optional single-service deployment. Railway builds `Dockerfile.railway`; Vercel builds from `frontend/`.

## Documentation map

| Document | Purpose |
|---|---|
| [`../README.md`](../README.md) | Project overview, stack, capabilities, local development, and active roadmap |
| [`API.md`](API.md) | Authentication bootstrap, profiles, ballots, feeds, voting, admin APIs, rate limits, SSE, and observability |
| [`FRONTEND-AUTH.md`](FRONTEND-AUTH.md) | In-memory access token, refresh rotation, immediate token revocation, retry, logout, and rollout behavior |
| [`SECURITY-HARDENING.md`](SECURITY-HARDENING.md) | Production fail-fast rules, token security version, origin checks, browser headers, security gates, and kill-tests |
| [`PROFILE.md`](PROFILE.md) | Private/public profile contracts, Ballot Mark presets, validation, and privacy boundaries |
| [`I18N.md`](I18N.md) | Current VI/EN locale behavior, catalogs, formatting, and verification |
| [`ADMIN.md`](ADMIN.md) | Admin role boundary, bootstrap, immutable audit log, and audited mutation rules |
| [`ADMIN-SEARCH.md`](ADMIN-SEARCH.md) | Protected user/ballot read APIs, filters, privacy fields, pagination, and query-count boundary |
| [`MODERATION.md`](MODERATION.md) | Ballot moderation states, public visibility, Redis/SSE convergence, and concurrency boundaries |
| [`ACCOUNT-MODERATION.md`](ACCOUNT-MODERATION.md) | User suspension, immediate access/refresh-token revocation, and admin safeguards |
| [`MAINTENANCE-MODE.md`](MAINTENANCE-MODE.md) | Persistent system mode, backend request enforcement, recovery APIs, audit, and rollout boundaries |
| [`SOCIAL-LOGIN.md`](SOCIAL-LOGIN.md) | Google OIDC, GitHub OAuth2, callback URLs, temporary OAuth session, and explicit linking |
| [`REALTIME-VOTES.md`](REALTIME-VOTES.md) | Public ballot SSE contract, convergence, bounded async delivery, and metrics |
| [`DEPLOY-VERCEL-RAILWAY.md`](DEPLOY-VERCEL-RAILWAY.md) | Active production deployment, required secrets/cookies, profiles, and verification |
| [`DOCUMENTATION-MAINTENANCE.md`](DOCUMENTATION-MAINTENANCE.md) | Documentation Definition of Done, audit procedure, and freshness history |
| [`../DESIGN.md`](../DESIGN.md) | Implemented Ballot Edition visual, interaction, responsive, profile, and i18n rules |
| [`../design/stitch/ballot-edition/`](../design/stitch/ballot-edition/) | Approved Stitch references; visual input, not production source |

## Source-of-truth order

When documentation and implementation differ:

1. Flyway, controllers, DTOs, `SecurityConfig`, runtime code, and active profile configuration.
2. Generated OpenAPI from a non-production environment on the same commit.
3. This documentation set.
4. Historical PR descriptions, Linear issues, and design exports.

Production secrets and current platform settings remain outside the repository. Do not infer them from example defaults alone.

## Implemented system boundaries

### Durable source of truth

PostgreSQL owns:

- local users, roles, account status, token security version, and linked provider identities;
- rotating refresh sessions and replay/revocation history;
- editable profile fields and preferred-locale value;
- ballots, lifecycle and moderation state, vote counts, score, and verdict state;
- individual user votes;
- append-only administrator audit records;
- the singleton system operating mode, localized public status copy, estimated end time, and last administrator actor.

Administrator soft-delete preserves ballot and vote rows. Account restriction preserves role, profile, linked identities, ballots, and votes.

### Derived infrastructure

Redis owns rebuildable or short-lived state:

- sliding-window rate-limit buckets;
- HOT, TOP_DAY, and TOP_WEEK sorted sets;
- shared application cache entries.

Ranked feeds fall back to latest-first PostgreSQL results when Redis is unavailable. Every ranked result is re-checked against PostgreSQL visibility, so a stale Redis member cannot expose a hidden or deleted ballot.

The operating mode is not stored in Redis. Status reads use a process-local five-second cache whose committed value is always re-loadable from PostgreSQL. Cross-instance cache invalidation is deferred until horizontal scaling.

Authentication rate limits do not fail open: login, registration, refresh, social authentication, and social linking return `503 RATE_LIMIT_UNAVAILABLE` when Redis cannot verify their bucket. Non-authentication rules may still follow the configured availability policy.

### Realtime delivery

SSE is a convergence channel, not a durable event log. It carries authoritative ballot aggregate snapshots and excludes `myVote`, identity data, and activity-history semantics.

After a vote transaction commits, ranking and SSE work are enqueued to separate bounded FIFO executors. After hide/delete commits, ranking membership is removed and existing SSE subscribers are completed asynchronously. PostgreSQL remains authoritative.

### Authentication and account enforcement

Register, login, and refresh return session fields plus the authenticated private profile. The normal frontend restore path uses one `POST /api/v1/auth/refresh` request and does not follow it with `/users/me`.

Access tokens remain in React memory. Refresh tokens remain in path-scoped `HttpOnly`, `Secure` cookies and are never returned in JSON or stored by frontend JavaScript.

Each access JWT carries the current role and `security_version`. PostgreSQL is checked after bearer-token authentication for every request carrying a Vote System JWT. The token role and version must exactly match the current user row, and the account must be active. Active-session logout, logout-all, administrator revoke-sessions, suspend/ban, explicit restore, and role promotion invalidate previously issued JWTs immediately. A missing, expired, or already-revoked logout cookie cannot rotate the version repeatedly. Temporary restriction expiry does not rotate the version a second time.

Cookie-authenticated refresh/logout/social-start requests validate browser Origin and Fetch Metadata. Every explicit Origin must match `CORS_ALLOWED_ORIGINS` exactly; request host and proxy metadata never create implicit trust. Browser POSTs without Origin are accepted only for `same-origin` or `none`; same-site and cross-site contexts are rejected with `SESSION_ORIGIN_REJECTED`. See [`FRONTEND-AUTH.md`](FRONTEND-AUTH.md), [`ACCOUNT-MODERATION.md`](ACCOUNT-MODERATION.md), and [`SECURITY-HARDENING.md`](SECURITY-HARDENING.md).

### Internationalization

System-owned UI copy is available in Vietnamese and English with Vietnamese as the default fallback. Guest resolution uses saved local preference, then supported browser language, then Vietnamese. Authenticated `preferredLocale` is applied once per resolved user without remounting the product shell. User-generated content is never auto-translated.

### Administrator boundary, moderation, search, and system status

`USER` and `ADMIN` are the only application roles. Registration and social onboarding always create `USER`. `/api/v1/admin/**` requires `ROLE_ADMIN` at both request and controller-method layers. Current database role is validated against every access JWT, so a stale or forged role claim is rejected. Controlled bootstrap can promote an existing account for one deployment; it is disabled by default and never creates credentials.

Administrator audit records are persisted as bounded JSONB rows through an internal append service. PostgreSQL rejects every audit-row update and delete through a trigger. Administrators can read deterministic paginated records through `GET /api/v1/admin/audit-logs`.

Ballot lifecycle (`OPEN/CLOSED`) is independent from ballot moderation (`VISIBLE/HIDDEN/DELETED`). Account authorization role is independent from account moderation (`ACTIVE/SUSPENDED/BANNED`). Both moderation domains use atomic state-and-audit transactions and pessimistic/concurrency safeguards.

Admin user/ballot search uses dedicated unrestricted read repositories that are never reused by public profile/feed code. Responses use explicit page DTOs, fixed `createdAt DESC, id DESC` ordering, escaped text matching, and batch provider/author hydration. Hidden/deleted ballots are visible only through protected admin routes.

`GET /api/v1/system/status` is anonymous and exposes only the public operating-mode contract. Protected administrator status reads and updates use `/api/v1/admin/system/status`. `READ_ONLY` and `MAINTENANCE` are enforced at the Spring Security request boundary while refresh/logout and administrator recovery routes remain reachable under their normal authorization rules. Status changes and immutable `SYSTEM_MODE_CHANGED` audit events commit atomically.

The static frontend cloaks `/admin` with neutral 404 behavior for guest and USER sessions and does not call administrator APIs. This is disclosure reduction only; backend role and current-token validation remain authoritative.

See [`ADMIN.md`](ADMIN.md), [`ADMIN-SEARCH.md`](ADMIN-SEARCH.md), [`MODERATION.md`](MODERATION.md), [`ACCOUNT-MODERATION.md`](ACCOUNT-MODERATION.md), and [`MAINTENANCE-MODE.md`](MAINTENANCE-MODE.md).

## Production profile and observability

`Dockerfile.railway` sets `SPRING_PROFILES_ACTIVE=production`.

`application-production.yml` disables OpenAPI/Swagger, verbose Spring/Hibernate logging, Hibernate statistics, and session-event metrics. It also requires an explicit non-placeholder JWT secret, canonical explicit HTTPS CORS origins, and Secure `__Secure-` refresh/OAuth cookie names. Unsafe or missing security configuration stops application startup.

Actuator exposes `health`, `info`, and `metrics`; only health is public. Every other `/actuator/**` route requires `ROLE_ADMIN`, including the discovery root and any endpoint exposed later.

Vercel responses include CSP, HSTS, frame denial, `nosniff`, strict-origin referrer policy, restrictive permissions policy, and COOP. Admin paths receive no-index headers.

Important metrics include:

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
vote.ranking.rebuild
vote.ranking.rebuild.duration
vote.ranking.rebuild.rows
vote.ranking.rebuild.redis.batches
vote.ranking.rebuild.lock.renewals
```

`X-Request-ID` is reused only when it matches the safe format; otherwise the backend generates one. Logs and metric tags must never contain user IDs, ballot IDs, emails, cookies, refresh-token hashes, or raw tokens.

## Supply-chain verification

The repository runs:

- backend unit/integration and production-container smoke tests;
- frontend unit/build/visual QA;
- administrator workspace QA;
- GitHub CodeQL Default Setup for repository code scanning;
- OSV dependency scanning;
- weekly Dependabot updates for Maven, npm, GitHub Actions, and Docker.

The combined Docker smoke explicitly activates the production profile, uses a temporary random JWT secret, validates secure cookie attributes and hidden Swagger routes, and scans uploaded artifacts to ensure they contain no refresh cookie or JWT.

A green workflow is evidence for the exact PR head only. Production secrets, Railway environment state, Vercel response headers, and live OAuth exchanges still require the deployment kill-tests in [`SECURITY-HARDENING.md`](SECURITY-HARDENING.md). Maintenance recovery evidence and the boundary between CI and a controlled production mutation are defined in [`TON-177-VERIFICATION.md`](TON-177-VERIFICATION.md).

## Active roadmap

Completed M4 foundations include i18n, profiles, Ballot Marks, admin authorization/audit/moderation/search, ranking operations, table-first admin UI, persistent system mode, backend maintenance enforcement, and System Operations controls.

Current sequence:

```text
TON-177  Production recovery and lockout QA
     ↓
TON-140  Unified reports and moderation cases
     ↓
TON-110/111  Comments, replies, voting, and moderation
     ↓
TON-112/113/114  Notifications, transactional outbox, and realtime delivery
     ↓
TON-115  Constrained direct messaging
```

## Documentation maintenance

Use [`DOCUMENTATION-MAINTENANCE.md`](DOCUMENTATION-MAINTENANCE.md) for the Definition of Done and audit process. Every PR must declare either `Docs updated: <files and reason>` or `Docs: N/A — <reason>`.
