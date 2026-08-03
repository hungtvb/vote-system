# Vote System API documentation

Controller mappings, DTOs, validation annotations, Flyway migrations, security configuration, and active Spring profiles are the implementation source of truth.

## Documentation visibility

Local/non-production:

```text
Swagger UI   http://localhost:8080/swagger-ui.html
OpenAPI JSON http://localhost:8080/v3/api-docs
OpenAPI YAML http://localhost:8080/v3/api-docs.yaml
Health       http://localhost:8080/actuator/health
```

Railway uses the `production` profile, which disables Swagger/OpenAPI. Public production health remains `GET /actuator/health`; other Actuator routes require authentication.

## Authentication and session bootstrap

Vote System uses:

- short-lived HS256 JWT access tokens held in frontend memory;
- rotating opaque refresh tokens in path-scoped `HttpOnly` cookies;
- SHA-256 token hashes and session history in PostgreSQL;
- replay detection that revokes all active sessions for the user.

```http
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
POST /api/v1/auth/logout-all
GET    /api/v1/auth/sessions
DELETE /api/v1/auth/sessions/{sessionId}
DELETE /api/v1/auth/sessions/others
```

Register, login, and refresh return the JWT session plus private profile. The refresh token is never returned in JSON. Each issued access JWT may carry a non-secret `session_family_id` claim so the authenticated user can identify the current refresh-session family; authorization remains based on the user, role, account status, and `security_version`, not on this claim.

The session list is private to the authenticated user. It exposes only the stable family ID, created/last-used/expiry timestamps, provider, a coarse allowlisted client label, and whether the family is current. It never exposes refresh tokens, token hashes, fingerprints, IP addresses, or raw user-agent values. Revoking one family cannot target the current family through this endpoint; `DELETE /others` preserves the current family. Revocation is authoritative on the next refresh, while already-issued access JWTs remain bounded by their short TTL.

```json
{
  "tokenType": "Bearer",
  "accessToken": "<jwt>",
  "expiresInSeconds": 900,
  "refreshExpiresInSeconds": 2592000,
  "userId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "email": "voter@example.com",
  "role": "USER",
  "profile": {
    "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "email": "voter@example.com",
    "displayName": "Public Voter Name",
    "initials": "PV",
    "bio": "Short public introduction",
    "avatarIcon": "CITIZEN",
    "avatarColor": "NAVY",
    "preferredLocale": "vi",
    "role": "USER",
    "linkedProviders": [],
    "createdAt": "2026-07-20T09:00:00Z",
    "updatedAt": "2026-07-30T10:00:00Z"
  }
}
```

Registration accepts email, password, and optional display name. When omitted, the backend stores a privacy-safe pseudonym.

## Social authentication

```http
GET  /api/v1/auth/social/providers
POST /api/v1/auth/social/{provider}/start
POST /api/v1/auth/social/{provider}/link/start
```

Public intents are `authenticate` and `create-ballot`. Link start requires a Vote System JWT. Provider callbacks are `/login/oauth2/code/google` and `/login/oauth2/code/github`.

Provider tokens, authorization codes, provider subjects, and email are never reflected to the frontend. Verified-email collisions require explicit authenticated linking. A restricted account receives a safe callback code:

```text
?social=error&code=account_unavailable&intent=authenticate
```

No refresh cookie or provider-link side effect is committed for that callback.

## Account access enforcement

Role and account status are independent:

```text
Role            USER | ADMIN
AccountStatus   ACTIVE | SUSPENDED | BANNED
```

PostgreSQL is authoritative. Every request carrying an authenticated JWT is checked after bearer authentication and before rate limiting/controller execution. Non-active accounts receive `401` for local login, social completion, refresh, authenticated REST, and authenticated SSE.

An expired temporary restriction is treated as `ACTIVE` without a scheduler. There is no account-status cache in this phase; database failure does not fail open.

Public profile and historical public content remain readable anonymously. Detailed behavior: [`ACCOUNT-MODERATION.md`](ACCOUNT-MODERATION.md) and [`FRONTEND-AUTH.md`](FRONTEND-AUTH.md).

## Profiles

```http
GET   /api/v1/users/me
PATCH /api/v1/users/me
GET   /api/v1/users/{userId}
```

Private profile includes email, role, preferred locale, linked providers, and editable profile data. Public profile/author summaries exclude email, role, preferred locale, sessions, and providers. Empty bio is represented as empty/null data and renders no placeholder as user content.

## Ballots

```http
GET    /api/v1/posts
POST   /api/v1/posts
GET    /api/v1/posts/{postId}
PUT    /api/v1/posts/{postId}
POST   /api/v1/posts/{postId}/close
DELETE /api/v1/posts/{postId}
```

Create payload:

```json
{
  "title": "Should the community fund the park?",
  "content": "Full proposal text",
  "category": "COMMUNITY",
  "closesAt": "2026-08-31T12:00:00Z",
  "verdictThreshold": 70
}
```

Title maximum is 200, content 20,000, category 50, and threshold 50–100. Only the author can update, close, or hard-delete a visible ballot.

Feeds accept `LATEST`, `HOT`, `TOP_DAY`, `TOP_WEEK`, and authenticated `MINE`, plus query/category/status/page/size filters. Every result is PostgreSQL-checked for `VISIBLE`; stale Redis IDs cannot expose or inflate pages with hidden/deleted ballots. Redis failure degrades ranked feeds to visible latest-first PostgreSQL results.

## Comments and replies

Comments are public reads under a publicly visible ballot and authenticated writes:

```http
GET    /api/v1/posts/{postId}/comments?limit=20&afterCreatedAt=...&afterId=...
POST   /api/v1/posts/{postId}/comments
PATCH  /api/v1/comments/{commentId}
DELETE /api/v1/comments/{commentId}
```

Create payload:

```json
{
  "body": "A bounded discussion argument",
  "parentId": null
}
```

`parentId` may reference only a visible top-level comment on the same ballot, so reply depth is exactly one level. Body text is trimmed, required, and limited to 2,000 characters.

Public ordering is `createdAt ASC, id ASC`. Cursor fields must be supplied together and remain stable when newer comments arrive. Responses batch-load privacy-safe author summaries containing display name, initials, and Ballot Mark. Removed or moderated rows remain safe tombstones with `body` omitted; previous text is never exposed publicly.

Authors may edit or remove only their own visible comments. Author removal is idempotent and decrements the ballot's visible `commentCount` under the same ballot lock used by comment creation. Closed ballots may still be discussed, but hidden/deleted ballots expose neither comments nor mutation existence.

COMMENT reports now validate against an existing visible comment and visible ballot, and self-reporting is rejected. Comment-specific administrator hide/restore/remove actions remain owned by TON-111.

## Voting and realtime

```http
PUT    /api/v1/posts/{postId}/vote
DELETE /api/v1/posts/{postId}/vote
GET    /api/v1/posts/{postId}/events
```

Vote payload is `{"type":"UP"}` or `DOWN`. Aggregate counts, score, and verdict update atomically. SSE is public only for visible active ballots, carries authoritative aggregate snapshots, excludes identity/`myVote`, and has no durable event history.

## Administrator authorization and audit

Every `/api/v1/admin/**` route requires `ROLE_ADMIN` at request and method levels. A restricted ADMIN is rejected by account enforcement before authorization succeeds.

```http
GET /api/v1/admin/probe
GET /api/v1/admin/audit-logs?page=0&size=20
```

Audit filters are exact `action`, `actorId`, `targetType`, and `targetId`. Ordering is `createdAt DESC, id DESC`. Arbitrary audit append is not exposed over HTTP. PostgreSQL rejects audit-row update/delete.

## Administrator ballot moderation

```http
POST /api/v1/admin/posts/{postId}/hide
POST /api/v1/admin/posts/{postId}/restore
POST /api/v1/admin/posts/{postId}/delete
```

Request:

```json
{"reason":"Violates published community rules"}
```

Ballot moderation is independent from `OPEN/CLOSED` lifecycle:

```text
VISIBLE -> HIDDEN
HIDDEN  -> VISIBLE
VISIBLE/HIDDEN -> DELETED
```

`DELETED` is terminal. State and audit append commit atomically. Hide/delete remove ranking membership and close SSE subscribers only after commit. Administrator soft-delete preserves ballot and vote rows. Public paths return generic not-found behavior. See [`MODERATION.md`](MODERATION.md).

## Administrator account moderation

```http
POST /api/v1/admin/users/{userId}/suspend
POST /api/v1/admin/users/{userId}/ban
POST /api/v1/admin/users/{userId}/restore
POST /api/v1/admin/users/{userId}/revoke-sessions
```

Suspend/ban request:

```json
{
  "reason": "Repeated abuse after warning",
  "until": "2026-08-15T12:00:00Z"
}
```

`until` is optional and limited to 365 days. Null means permanent. Restore/revoke-session requests contain only required `reason`.

Response:

```json
{
  "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "accountStatus": "SUSPENDED",
  "statusUntil": "2026-08-15T12:00:00Z",
  "statusUpdatedAt": "2026-07-30T11:00:00Z",
  "revokedSessions": 2
}
```

Restriction, refresh-session revocation, and immutable audit append commit in one transaction. Audit failure rolls back both state and revocation. Repeated/incompatible transitions return `409`.

Safeguards:

- no self suspend/ban/revoke-session;
- no restriction of the last effective active administrator;
- concurrent admin decisions serialized with PostgreSQL advisory locking;
- refresh, user-driven revocation, and moderation share `user -> refresh sessions` lock order.

Explicit `revoke-sessions` leaves account status/role unchanged. Existing access JWTs remain valid until expiry, but refresh cookies become unusable. See [`ACCOUNT-MODERATION.md`](ACCOUNT-MODERATION.md).

## Administrator search APIs

Protected read APIs for the moderation workspace:

```http
GET /api/v1/admin/users
GET /api/v1/admin/users/{userId}
GET /api/v1/admin/posts
GET /api/v1/admin/posts/{postId}
Authorization: Bearer <admin access token>
```

User filters are exact ID, case-insensitive email/display-name query, role, effective account status, and inclusive created-at range. Ballot filters are exact ID, ballot number, case-insensitive title/content query, author, category, lifecycle status, moderation status, and inclusive created-at range.

List responses use an explicit page DTO, zero-based pages, default size 20, maximum size 100, and fixed `createdAt DESC, id DESC` ordering. Text filters escape SQL LIKE wildcards. Hidden and deleted ballots are available only through the protected administrator path.

User responses exclude credential, provider-subject, session/token, IP, and raw user-agent fields. Ballot responses exclude individual voter identities and vote rows. Provider and author context is batch-loaded to avoid N+1 queries. See [`ADMIN-SEARCH.md`](ADMIN-SEARCH.md).

## Rate limiting

| Rule | Default | Identity |
|---|---:|---|
| Login | 5/minute | IP |
| Register | 3/hour | IP |
| Refresh | 20/minute | IP |
| Social start | 20/minute | IP |
| Create ballot | 10/hour | user |
| Vote | 30/minute | user |
| Profile update | 10/hour | user |
| Comment create | 20/10 minutes | user |
| Comment edit | 30/10 minutes | user |
| Session revocation | 10/minute | user |

Redis Lua implements atomic sliding windows. Rejection returns `429` plus `Retry-After`. Default infrastructure-error behavior is fail-open; account-status PostgreSQL enforcement is separate and does not fail open.

## Error, CORS, and observability

Validation/authentication/authorization/resource/conflict/rate-limit failures use stable HTTP status and Problem Details-compatible JSON where handled by the application. Frontend code must not parse free-form text for logic.

Credentialed CORS uses explicit origins and exposes `Retry-After` and `X-Request-ID`. Wildcard origin is not used with cookies.

Important metrics:

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

Metric tags and logs must not contain user/ballot IDs, email, cookies, refresh hashes, or raw tokens.
