# Vote System API documentation

The backend generates OpenAPI from Spring MVC controllers through springdoc-openapi. Controller mappings, DTOs, validation annotations, Flyway migrations, security configuration, and active Spring profiles remain the implementation source of truth.

## API documentation visibility

Local and non-production Spring Boot runs expose:

```text
Swagger UI   http://localhost:8080/swagger-ui.html
OpenAPI JSON http://localhost:8080/v3/api-docs
OpenAPI YAML http://localhost:8080/v3/api-docs.yaml
Health       http://localhost:8080/actuator/health
```

Railway runs with `SPRING_PROFILES_ACTIVE=production`. `application-production.yml` disables both OpenAPI generation and Swagger UI, so production verification should expect those routes to be unavailable. Public production health remains:

```http
GET https://api.ballotbox.io.vn/actuator/health
```

Other Actuator routes, including `info` and `metrics`, require authentication.

## Session and auth bootstrap

Email/password and social authentication converge on one Vote System session model:

- short-lived HS256 JWT access token held in frontend memory;
- opaque rotating refresh token in an `HttpOnly` cookie;
- SHA-256 refresh-token hash and session metadata in PostgreSQL;
- replay detection that revokes all active refresh sessions for the affected user.

Default access-token lifetime is 15 minutes. Default refresh-token lifetime is 30 days.

Register, login, and refresh return existing session fields plus the authenticated private profile:

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
    "linkedProviders": ["GOOGLE"],
    "createdAt": "2026-07-20T09:00:00Z",
    "updatedAt": "2026-07-29T01:00:00Z"
  }
}
```

The refresh token is never returned in JSON. Top-level `userId`, `email`, and `role` remain during the backward-compatibility window.

### Email/password endpoints

```http
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
POST /api/v1/auth/logout-all
```

Registration accepts:

```json
{
  "email": "voter@example.com",
  "password": "strong-password",
  "displayName": "Public Voter Name"
}
```

`displayName` is optional during registration. When omitted, the backend persists a privacy-safe pseudonym instead of deriving public identity from email.

Login, registration, refresh, and a successful social callback write the rotating refresh cookie. The normal frontend restore/login/register path uses the returned `profile` and does not make a second `/users/me` request.

Detailed browser behavior: [`FRONTEND-AUTH.md`](FRONTEND-AUTH.md).

## Google and GitHub social authentication

Configured provider discovery:

```http
GET /api/v1/auth/social/providers
```

```json
{
  "providers": ["google", "github"]
}
```

An installation without provider credentials returns an empty list and keeps email/password auth usable.

Start public authentication or create-ballot continuation:

```http
POST /api/v1/auth/social/google/start
Content-Type: application/json

{"intent":"authenticate"}
```

Allowed public intents are `authenticate` and `create-ballot`.

Start explicit linking from an authenticated account:

```http
POST /api/v1/auth/social/github/link/start
Authorization: Bearer <access-token>
```

Provider callbacks:

```text
/login/oauth2/code/google
/login/oauth2/code/github
```

Callback redirects contain only safe status metadata. Provider tokens, authorization codes, email, and provider subjects are never reflected to the frontend. Provider identities are keyed by `(provider, providerSubject)`. Verified-email collisions require explicit authenticated linking; accounts are not silently merged by email.

After callback, the frontend restores session/profile through `POST /api/v1/auth/refresh`.

Detailed setup and security rules: [`SOCIAL-LOGIN.md`](SOCIAL-LOGIN.md).

## Voter identity and profile

Private profile retrieval:

```http
GET /api/v1/users/me
Authorization: Bearer <access-token>
```

Profile update:

```http
PATCH /api/v1/users/me
Authorization: Bearer <access-token>
Content-Type: application/json
```

Public-safe lookup:

```http
GET /api/v1/users/{userId}
```

The private profile contains local user ID, optional account email, public display name, initials, optional bio, Ballot Mark icon/color, preferred locale, role, linked providers, and timestamps.

Public profile and ballot-author responses exclude email, role, preferred locale, sessions, and linked providers. An empty bio is represented as empty/null data; the public UI renders no placeholder as user-authored content.

Detailed contract and enum values: [`PROFILE.md`](PROFILE.md). Locale behavior: [`I18N.md`](I18N.md).

## Ballot lifecycle

Create:

```http
POST /api/v1/posts
Authorization: Bearer <access-token>
Content-Type: application/json

{
  "title": "Should the community fund the park?",
  "content": "Full proposal text",
  "category": "COMMUNITY",
  "closesAt": "2026-08-31T12:00:00Z",
  "verdictThreshold": 70
}
```

Validation boundaries:

- `title`: required, maximum 200 characters;
- `content`: required, maximum 20,000 characters;
- `category`: optional, maximum 50 characters;
- `verdictThreshold`: optional integer from 50 through 100;
- `closesAt`: optional timestamp validated by domain rules.

Operations:

```http
GET    /api/v1/posts
POST   /api/v1/posts
GET    /api/v1/posts/{postId}
PUT    /api/v1/posts/{postId}
POST   /api/v1/posts/{postId}/close
DELETE /api/v1/posts/{postId}
```

Only the author can update, close, or hard-delete a visible ballot. The backend owns lifecycle transitions and final-verdict semantics.

## Administrator ballot moderation

Lifecycle and moderation are independent:

```text
BallotStatus       OPEN | CLOSED
ModerationStatus   VISIBLE | HIDDEN | DELETED
```

Administrator mutations:

```http
POST /api/v1/admin/posts/{postId}/hide
POST /api/v1/admin/posts/{postId}/restore
POST /api/v1/admin/posts/{postId}/delete
Authorization: Bearer <admin access token>
Content-Type: application/json

{"reason":"Violates published community rules"}
```

`reason` is required and limited to 500 characters.

Response:

```json
{
  "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "moderationStatus": "HIDDEN",
  "moderationUpdatedAt": "2026-07-30T09:00:00Z"
}
```

Allowed transitions:

```text
VISIBLE -> HIDDEN
HIDDEN  -> VISIBLE
VISIBLE -> DELETED
HIDDEN  -> DELETED
```

`DELETED` is terminal. Invalid or repeated transitions return `409 Conflict` and do not create another successful-action audit record.

Each transition and its matching immutable audit append commit in the same PostgreSQL transaction. Hide/delete remove ranking membership after commit; restore re-inserts the current authoritative ballot aggregate. Administrator soft-delete retains the ballot and vote rows.

Hidden and deleted ballots return generic `404 Post not found` behavior through public detail, voting, SSE, and owner mutation endpoints. Public responses do not expose moderation state, reason, audit metadata, or administrator identity.

Detailed transition, concurrency, Redis, and SSE contract: [`MODERATION.md`](MODERATION.md). Authorization and audit-log contract: [`ADMIN.md`](ADMIN.md).

## Administrator audit-log read API

```http
GET /api/v1/admin/audit-logs?page=0&size=20
Authorization: Bearer <admin access token>
```

Optional exact filters are `action`, `actorId`, `targetType`, and `targetId`. Responses use an explicit stable page DTO ordered by `createdAt DESC, id DESC`. Arbitrary audit append is not exposed as an HTTP endpoint.

## Feeds, search, and filters

`GET /api/v1/posts` accepts:

- `feed`: `LATEST`, `HOT`, `TOP_DAY`, `TOP_WEEK`, or `MINE`; default `LATEST`;
- `query`: optional, maximum 200 characters; case-insensitive substring search across title, content, and ballot number; `%` and `_` are literal;
- `category`: optional, maximum 50 characters; exact match after upper-case normalization;
- `status`: optional `OPEN` or `CLOSED`;
- `page`: zero-based, minimum `0`;
- `size`: `1` through `100`, default `20`.

Examples:

```http
GET /api/v1/posts?feed=LATEST&query=architecture&category=TECHNOLOGY&status=OPEN&page=0&size=20
```

```http
GET /api/v1/posts?feed=MINE&page=0&size=20
Authorization: Bearer <access-token>
```

`MINE` is backend-owned and requires authentication. Clients must not emulate it by filtering a public page.

Ordering:

- `LATEST`: PostgreSQL `createdAt DESC`;
- `MINE`: authenticated author's `createdAt DESC`;
- `HOT`: Redis hot-score order;
- `TOP_DAY`: current UTC-day Redis order;
- `TOP_WEEK`: current UTC-week Redis order.

Every feed returns only `VISIBLE` ballots. Ranked IDs are re-checked against PostgreSQL visibility before serialization, so a stale Redis member cannot expose a hidden or deleted ballot. Filtering applies across the complete ranked set before pagination. If Redis is unavailable, ranked feeds degrade to matching visible PostgreSQL results in latest-first order.

## Voting

```http
PUT /api/v1/posts/{postId}/vote
Authorization: Bearer <access-token>
Content-Type: application/json

{"type":"UP"}
```

```http
DELETE /api/v1/posts/{postId}/vote
Authorization: Bearer <access-token>
```

The backend supports add, change, repeated-choice removal, and explicit removal. Aggregate counts and score update atomically; `(user_id, post_id)` uniqueness prevents duplicate rows. Voting requires a visible ballot and the aggregate update query repeats that visibility predicate as defense in depth.

Ballot responses expose authoritative:

- `upVotes`;
- `downVotes`;
- `totalVotes`;
- `voteScore`;
- authenticated `myVote`;
- threshold and projected/final verdict;
- lifecycle and timestamps.

Moderation status is intentionally excluded from public ballot responses.

## Realtime vote updates

```http
GET /api/v1/posts/{postId}/events
Accept: text/event-stream
```

The public stream is available only for visible, active ballots. It sends an authoritative aggregate snapshot, heartbeat comments, reconnect hint, and future `vote-update` events. It excludes `myVote` and identity data.

After commit, SSE delivery is enqueued to a bounded FIFO executor. The vote HTTP response does not normally wait for connected-client network I/O. Event history is not persisted; reconnect converges through the latest database snapshot. Hide/delete completes existing subscribers after the moderation transaction commits.

Detailed contract: [`REALTIME-VOTES.md`](REALTIME-VOTES.md).

## Redis rate limiting

| Rule | Default | Identity |
|---|---:|---|
| Login | 5 / minute | IP |
| Register | 3 / hour | IP |
| Refresh | 20 / minute | IP |
| Social start | 20 / minute | IP |
| Create ballot | 10 / hour | authenticated user |
| Vote | 30 / minute | authenticated user |
| Profile update | 10 / hour | authenticated user |

The implementation uses an atomic Redis sorted-set/Lua sliding window. Rejected requests return `429 Too Many Requests` with `Retry-After`. Default behavior is fail-open when Redis cannot be reached; configure `RATE_LIMIT_FAIL_OPEN=false` only after validating Redis reliability.

## Error contract and localization

Validation, authentication, authorization, resource, conflict, and rate-limit failures use Problem Details-compatible JSON. Frontend code branches on HTTP status and stable machine-owned fields rather than parsing free-form text.

User-facing system copy is mapped through VI/EN catalogs. Backend domain values remain language-neutral. User-generated content is not translated automatically.

## Health, metrics, and request correlation

Public:

```http
GET /actuator/health
```

Authenticated Actuator access includes `info` and `metrics`.

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
```

Responses include `X-Request-ID`. A safe inbound value is reused; otherwise a UUID is generated. Completion logs use route templates rather than concrete ballot UUID paths.

User IDs, ballot IDs, email, cookies, refresh-token hashes, raw tokens, and dynamic exception text must not become metric tags.

## CORS

Credentialed frontend requests require an explicit origin allow-list. Wildcard `*` is not used with cookies. Exposed response headers:

```text
Retry-After
X-Request-ID
```

Production frontend origin:

```text
https://app.ballotbox.io.vn
```

## Source of truth

Key configuration:

```text
src/main/java/com/hungtvb/votesystem/common/config/OpenApiConfig.java
src/main/java/com/hungtvb/votesystem/common/config/SecurityConfig.java
src/main/resources/application.yml
src/main/resources/application-production.yml
```

For exact schemas, use controller/DTO source or generated OpenAPI from a non-production environment on the same commit.
