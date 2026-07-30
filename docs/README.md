# Vote System documentation

This directory documents the current `main` branch. Controller mappings, DTOs, Flyway migrations, security configuration, runtime source, and active Spring profiles remain the implementation source of truth.

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
| [`API.md`](API.md) | Authentication bootstrap, profiles, ballots, feeds, voting, rate limits, SSE, and observability |
| [`FRONTEND-AUTH.md`](FRONTEND-AUTH.md) | In-memory access token, refresh rotation, single-request restore, retry, logout, and rollout behavior |
| [`PROFILE.md`](PROFILE.md) | Private/public profile contracts, Ballot Mark presets, validation, and privacy boundaries |
| [`I18N.md`](I18N.md) | Current VI/EN locale behavior, catalogs, formatting, and verification |
| [`ADMIN.md`](ADMIN.md) | Admin role boundary, controlled bootstrap, immutable audit log, protected read API, privacy, and retention boundaries |
| [`SOCIAL-LOGIN.md`](SOCIAL-LOGIN.md) | Google OIDC, GitHub OAuth2, callback URLs, temporary OAuth session, and explicit linking |
| [`REALTIME-VOTES.md`](REALTIME-VOTES.md) | Public ballot SSE contract, convergence, bounded async delivery, and metrics |
| [`DEPLOY-VERCEL-RAILWAY.md`](DEPLOY-VERCEL-RAILWAY.md) | Active production deployment, profiles, variables, and verification |
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

- local users and linked provider identities;
- rotating refresh sessions and replay/revocation history;
- editable profile fields and preferred-locale value;
- ballots, lifecycle metadata, vote counts, score, and verdict state;
- individual user votes;
- append-only administrator audit records.

### Derived infrastructure

Redis owns rebuildable or short-lived state:

- sliding-window rate-limit buckets;
- HOT, TOP_DAY, and TOP_WEEK sorted sets;
- shared application cache entries.

Ranked feeds fall back to latest-first PostgreSQL results when Redis is unavailable. Ranking state can be rebuilt from PostgreSQL.

### Realtime delivery

SSE is a convergence channel, not a durable event log. It carries authoritative ballot aggregate snapshots and excludes `myVote`, identity data, and activity-history semantics.

After a vote transaction commits, ranking and SSE work are enqueued to separate bounded FIFO executors. The HTTP response does not wait for ordinary Redis or connected-client network I/O. PostgreSQL remains authoritative.

### Authentication and profile bootstrap

Register, login, and refresh return session fields plus the authenticated private profile. The normal frontend restore path uses one `POST /api/v1/auth/refresh` request and does not follow it with `/users/me`. A temporary compatibility fallback remains for a rolling deployment that reaches an older backend response.

Access tokens remain in React memory. Refresh tokens remain in path-scoped `HttpOnly` cookies and are never returned in JSON or stored by frontend JavaScript.

### Internationalization

System-owned UI copy is available in Vietnamese and English with Vietnamese as the default fallback. Guest resolution uses saved local preference, then supported browser language, then Vietnamese. Authenticated `preferredLocale` is applied once per resolved user without remounting the product shell. User-generated content is never auto-translated.

### Administrator boundary and audit trail

`USER` and `ADMIN` are the only application roles. Registration and social onboarding always create `USER`. `/api/v1/admin/**` requires `ROLE_ADMIN` at both the request and controller-method layers. Controlled bootstrap can promote an existing account for one deployment; it is disabled by default and never creates credentials.

Administrator audit records are persisted as bounded JSONB rows through an internal append service. The application exposes no update/delete repository methods, and PostgreSQL rejects every audit-row update or delete through a trigger. Administrators can read deterministic paginated records through `GET /api/v1/admin/audit-logs`. See [`ADMIN.md`](ADMIN.md).

## Production profile and observability

`Dockerfile.railway` sets:

```text
SPRING_PROFILES_ACTIVE=production
```

`application-production.yml`:

- disables OpenAPI JSON and Swagger UI;
- sets Spring Web and Hibernate logging to INFO;
- disables Hibernate statistics and session-event metrics.

The base `application.yml` keeps verbose diagnostic settings for local/performance investigation, but they are overridden in Railway production.

Actuator exposes `health`, `info`, and `metrics`; only health is public. Important metrics include:

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

`X-Request-ID` is reused only when it matches the safe format; otherwise the backend generates one. Logs and metric tags must never contain user IDs, ballot IDs, emails, cookies, refresh-token hashes, or raw tokens.

## Active roadmap

Completed M4 foundations:

```text
TON-116  Vietnamese/English i18n foundation
TON-108  Editable profile and Ballot Mark identity
TON-187  Custom Ballot Mark SVG set
TON-189  Empty public-bio cleanup
TON-191  Locale fallback and authenticated preference sync
TON-192  Admin authorization boundary and controlled bootstrap
```

Current sequence:

```text
TON-109  Admin roles, audit log, moderation, and operational dashboard
  TON-194  Immutable admin audit log foundation
     ↓
  moderation and operational admin mutations
     ↓
  protected admin dashboard
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

Use [`DOCUMENTATION-MAINTENANCE.md`](DOCUMENTATION-MAINTENANCE.md) for the Definition of Done and audit process. Every PR must declare either:

```text
Docs updated: <files and reason>
```

or:

```text
Docs: N/A — <reason>
```
