# Vote System API documentation

The backend generates its OpenAPI specification from the Spring MVC controllers through springdoc-openapi.

## Local URLs

After starting the Spring Boot application on port `8080`:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- OpenAPI YAML: `http://localhost:8080/v3/api-docs.yaml`

The documentation routes are public. API operations that require authentication use the `bearerAuth` HTTP security scheme.

## Authorize Swagger UI

1. Register or log in through an auth endpoint.
2. Copy the returned access token.
3. Open Swagger UI and select **Authorize**.
4. Paste the access token. Swagger UI adds the `Bearer` prefix automatically.

The refresh token is stored in an `HttpOnly` cookie and is not entered into the Swagger authorization dialog.

## Voter identity

Registration accepts an optional public `displayName` in addition to `email` and `password`. When it is omitted, the backend creates a privacy-safe pseudonym instead of deriving a public identity from the email address.

Authenticated clients can load the private Voter ID profile with:

```http
GET /api/v1/users/me
Authorization: Bearer <access-token>
```

The response contains the account email, public display name, initials, role, and account timestamps. Ballot feed/detail responses retain the top-level `authorId` for compatibility and also include a public `author` object with only `id`, `displayName`, and `initials`. Author email is never embedded in a public ballot response.

## Ballot feeds, search, and filters

`GET /api/v1/posts` accepts:

- `feed`: `LATEST`, `HOT`, `TOP_DAY`, `TOP_WEEK`, or `MINE`; default `LATEST`.
- `query`: optional, maximum 200 characters. Case-insensitive substring search across title, content, and ballot number. SQL wildcard characters such as `%` and `_` are treated literally.
- `category`: optional, maximum 50 characters. Matching is exact after upper-case normalization.
- `status`: optional `OPEN` or `CLOSED`.
- `page`: zero-based page index; must be at least `0`.
- `size`: page size from `1` through `100`; default `20`.

Examples:

```http
GET /api/v1/posts?feed=LATEST&query=architecture&category=TECHNOLOGY&status=OPEN&page=0&size=20
```

```http
GET /api/v1/posts?feed=MINE&page=0&size=20
Authorization: Bearer <access-token>
```

`MINE` is backend-owned and requires authentication. It returns only ballots authored by the current JWT subject; clients must not emulate this feed by filtering an already-loaded public page.

### Ordering semantics

- `LATEST`: matching ballots ordered by `createdAt` descending in PostgreSQL.
- `MINE`: the current user's matching ballots ordered by `createdAt` descending in PostgreSQL.
- `HOT`: matching ballots retain the Redis hot-score order.
- `TOP_DAY`: matching ballots retain the current UTC-day Redis ranking.
- `TOP_WEEK`: matching ballots retain the current UTC-week Redis ranking.

For filtered ranked feeds, filtering is applied across the complete ranked set before the requested page is selected. `totalElements` and `totalPages` therefore describe the filtered dataset, not the raw Redis set or only the records already loaded by a frontend client.

If Redis is unavailable, ranked feeds use the existing database fallback and return matching ballots in latest-first order. This is a degraded availability mode; the response remains filtered and paginated correctly, but rank order is temporarily unavailable.

## Realtime vote updates

An active ballot exposes a public Server-Sent Events endpoint:

```http
GET /api/v1/posts/{postId}/events
Accept: text/event-stream
```

The server immediately sends the current authoritative aggregate unless the request's `Last-Event-ID` already matches the current ballot update timestamp. Committed vote changes produce `vote-update` events containing shared counts, score, threshold, verdict, and `updatedAt`. Events deliberately exclude `myVote` and account data.

The stream sends heartbeat comments, recommends a three-second reconnect delay, disables caching/proxy buffering where supported, and removes disconnected emitters. Missing ballots return `404`; ballots that no longer accept votes return `409`.

The SSE history is not persisted. Reconnection converges through the current database snapshot rather than replaying intermediate activity. Full payload, reconnect, deployment, frontend fallback, and scope details are documented in `docs/REALTIME-VOTES.md`.

## Source of truth

Controller mappings and request/response DTOs are the source of truth for generated operations and schemas. Global API metadata and JWT security configuration are defined in:

`src/main/java/com/hungtvb/votesystem/common/config/OpenApiConfig.java`
