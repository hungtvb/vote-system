# Vote System API documentation

The backend generates its OpenAPI specification from Spring MVC controllers through springdoc-openapi. Controller mappings, DTOs, validation annotations, Flyway migrations, and security configuration remain the implementation source of truth.

## Local URLs

After starting Spring Boot on port `8080`:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- OpenAPI YAML: `http://localhost:8080/v3/api-docs.yaml`
- Public health: `http://localhost:8080/actuator/health`

Swagger/OpenAPI and health routes are public. Other Actuator routes, including metrics, require authentication. Protected API operations use the `bearerAuth` HTTP security scheme.

## Session contract

Email/password and social authentication converge on the same Vote System session model:

- short-lived HS256 JWT access token, held in frontend memory;
- opaque rotating refresh token in an `HttpOnly` cookie;
- SHA-256 refresh-token hash and session metadata persisted in PostgreSQL;
- replay detection that revokes all active sessions for the affected user.

Default access-token lifetime is 15 minutes. Default refresh-token lifetime is 30 days.

## Email/password authentication

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

`displayName` is optional. When omitted, the backend persists a privacy-safe pseudonym instead of deriving a public identity from the email address.

The refresh token is not entered into Swagger authorization. Login, registration, refresh, or a successful social callback writes it as an `HttpOnly` cookie.

## Google and GitHub social authentication

Load providers configured on the current backend:

```http
GET /api/v1/auth/social/providers
```

```json
{
  "providers": ["google", "github"]
}
```

An installation without social credentials returns an empty array and continues to support email/password authentication.

Start public authentication or pending create-ballot continuation:

```http
POST /api/v1/auth/social/google/start
Content-Type: application/json

{"intent":"authenticate"}
```

Allowed public intents are `authenticate` and `create-ballot`. The response contains a backend-generated authorization URL.

Start explicit linking from an authenticated account:

```http
POST /api/v1/auth/social/github/link/start
Authorization: Bearer <access-token>
```

Spring Security handles provider callbacks at:

```text
/login/oauth2/code/google
/login/oauth2/code/github
```

The callback redirect never contains Vote System tokens, provider tokens, authorization codes, email, or provider subject. Provider identities are keyed by `(provider, providerSubject)`. Verified-email collisions require explicit authenticated linking; accounts are never silently merged by email.

Detailed setup and security rules: [`SOCIAL-LOGIN.md`](SOCIAL-LOGIN.md).

## Voter identity

```http
GET /api/v1/users/me
Authorization: Bearer <access-token>
```

The private profile contains:

- local user ID;
- optional account email;
- public display name;
- initials;
- role;
- linked social providers;
- account timestamps.

A social-only account may have `email: null`. Public ballot responses include only a safe author summary with `id`, `displayName`, and `initials`; they do not expose email or linked-provider data.

Editable bio, Ballot Mark avatar presets, and `preferredLocale` are planned under TON-108/TON-116 and are not part of the current API yet.

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

Only the author can update, close, or delete a ballot. The backend owns OPEN/CLOSED lifecycle transitions and final-verdict semantics.

## Ballot feeds, search, and filters

`GET /api/v1/posts` accepts:

- `feed`: `LATEST`, `HOT`, `TOP_DAY`, `TOP_WEEK`, or `MINE`; default `LATEST`;
- `query`: optional, maximum 200 characters; case-insensitive substring search across title, content, and ballot number; `%` and `_` are treated literally;
- `category`: optional, maximum 50 characters; exact match after upper-case normalization;
- `status`: optional `OPEN` or `CLOSED`;
- `page`: zero-based index, minimum `0`;
- `size`: `1` through `100`, default `20`.

Examples:

```http
GET /api/v1/posts?feed=LATEST&query=architecture&category=TECHNOLOGY&status=OPEN&page=0&size=20
```

```http
GET /api/v1/posts?feed=MINE&page=0&size=20
Authorization: Bearer <access-token>
```

`MINE` is backend-owned and requires authentication. Clients must not emulate it by filtering an already-loaded public page.

### Ordering semantics

- `LATEST`: matching ballots ordered by `createdAt DESC` in PostgreSQL;
- `MINE`: the current user's matching ballots ordered by `createdAt DESC`;
- `HOT`: matching ballots retain Redis hot-score order;
- `TOP_DAY`: matching ballots retain current UTC-day Redis ranking;
- `TOP_WEEK`: matching ballots retain current UTC-week Redis ranking.

Filtering applies across the complete ranked set before selecting a page. `totalElements` and `totalPages` therefore describe the filtered dataset, not only the rows already loaded by the frontend.

If Redis is unavailable, ranked feeds degrade to matching PostgreSQL results in latest-first order. Filtering and pagination remain correct, but rank order is temporarily unavailable.

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

The backend supports add, change, repeated-choice removal, and explicit removal semantics. Aggregate counts and score are updated atomically while the individual `(user_id, post_id)` vote is protected by a database uniqueness constraint.

Ballot responses expose authoritative:

- `upVotes`;
- `downVotes`;
- `totalVotes`;
- `voteScore`;
- `myVote` for authenticated REST responses;
- verdict threshold and projected/final verdict;
- lifecycle status and timestamps.

## Realtime vote updates

Active ballots expose a public SSE endpoint:

```http
GET /api/v1/posts/{postId}/events
Accept: text/event-stream
```

The server sends an authoritative aggregate snapshot, heartbeat comments, a reconnect hint, and future `vote-update` events. Events deliberately exclude `myVote` and identity data.

After the database transaction commits, SSE delivery is enqueued to a bounded FIFO executor. The HTTP vote response does not normally wait for connected-client network I/O. Event history is not persisted; reconnect converges through the latest database snapshot.

Detailed contract: [`REALTIME-VOTES.md`](REALTIME-VOTES.md).

## Redis rate limiting

Default policies:

| Rule | Limit | Identity |
|---|---:|---|
| Login | 5 / minute | IP |
| Register | 3 / hour | IP |
| Refresh | 20 / minute | IP |
| Social start | 20 / minute | IP |
| Create ballot | 10 / hour | authenticated user |
| Vote | 30 / minute | authenticated user |

The implementation uses an atomic Redis sorted-set/Lua sliding window. Rejected requests return `429 Too Many Requests` with `Retry-After`. The default is fail-open when Redis cannot be reached; this is configurable through `RATE_LIMIT_FAIL_OPEN`.

## Error contract

Validation, authentication, authorization, resource, conflict, and rate-limit failures use Problem Details-compatible JSON. Frontend code should branch on HTTP status and stable machine-owned fields rather than parsing free-form text.

TON-116 will formalize localized frontend error mapping. Backend domain values remain language-neutral; user-generated content is not translated automatically.

## Health, metrics, and request correlation

Public:

```http
GET /actuator/health
```

Authenticated Actuator access includes `info` and `metrics` because only health is explicitly public in Spring Security.

Important metric names:

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

API responses include `X-Request-ID`. A safe inbound value is reused; otherwise the backend generates a UUID. Request logs use bounded route templates, not concrete ballot UUID paths.

No user ID, ballot ID, email, cookie, refresh-token hash, raw token, or dynamic exception text may be added as a metric tag.

## CORS

Credentialed frontend requests require an explicit origin allow-list. Wildcard `*` is not used with cookies. Exposed response headers include:

```text
Retry-After
X-Request-ID
```

Current production frontend origin:

```text
https://app.ballotbox.io.vn
```

## Source of truth

Global OpenAPI and JWT configuration:

```text
src/main/java/com/hungtvb/votesystem/common/config/OpenApiConfig.java
src/main/java/com/hungtvb/votesystem/common/config/SecurityConfig.java
```

For exact request/response schemas, use the generated OpenAPI document from the deployed backend or the controller/DTO source on the same commit.