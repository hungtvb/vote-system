# Frontend authentication lifecycle

## Runtime model

The browser keeps the short-lived Vote System access token in React memory only. The long-lived refresh token is stored by the backend in an `HttpOnly` cookie, so frontend JavaScript never reads or persists it.

The frontend API layer is split by responsibility:

- `transport.ts`: base URL, credentialed fetch, Problem Details normalization, typed `ApiError`, `Retry-After`, and request headers;
- `auth-api.ts`: register, login, refresh, logout, logout-all, and the single-flight refresh lock;
- `social-auth-api.ts`: enabled-provider discovery, social authorization start, and authenticated link start;
- `user-api.ts`: current Voter ID profile and linked-provider state;
- `ballot-api.ts`: ballot CRUD, feeds, filters, voting, and detail endpoints;
- `authorized-request.ts`: one access-token retry after a 401;
- `useSession.ts`: React session/profile state, restore, backend logout, and local clearing.

Google/GitHub provider tokens never enter this frontend lifecycle. Spring Security performs authorization-code exchange on the backend, creates the normal Vote System refresh session, discards the temporary OAuth session, and redirects with safe status metadata only.

## Public feed and startup restore

Public content no longer waits for refresh-session restoration.

On application startup:

1. LATEST/HOT/TOP feed requests start immediately as public requests.
2. In parallel, the application calls `POST /api/v1/auth/refresh` with `credentials: include`.
3. The backend validates and rotates the refresh cookie.
4. The frontend calls `GET /api/v1/users/me` with the returned access token.
5. The UI enters authenticated state only after refresh and profile loading both succeed.
6. The first public page is reconciled in the background after authentication so `myVote` is authoritative without hiding the already-rendered feed.

`MINE` is different: it waits until restore completes and a valid session exists because the backend owns author isolation.

Superseded feed requests are aborted through `AbortController`. Abort errors do not surface as product errors. A request-sequence guard remains as a secondary defense against stale responses.

A 401 or 403 during restore produces a clean guest state. Other restore failures are recorded without exposing secrets and also leave the UI signed out.

The same restore path runs after Google/GitHub callback. Social login does not create a second frontend session type.

## Enabled social providers

At startup the app calls:

```http
GET /api/v1/auth/social/providers
```

Only configured providers are rendered. An empty list leaves email/password authentication fully usable. A provider already linked to the profile displays as linked; a missing provider receives a link action only when it is enabled on the backend.

## Starting social authentication

The auth dialog sends an allowlisted intent:

```http
POST /api/v1/auth/social/google/start
Content-Type: application/json

{"intent":"authenticate"}
```

or:

```json
{"intent":"create-ballot"}
```

The backend returns its own `/oauth2/authorization/{provider}` URL. The frontend never submits an arbitrary return URL, callback URL, provider token, email, or account identifier.

The browser navigates with `window.location.assign`. If start fails, the dialog remains usable and displays a safe backend problem; an existing Vote System session is not cleared.

## Social callback continuation

Configured success/failure redirects contain only allowlisted parameters:

```text
?social=success&provider=google&intent=create-ballot
?social=linked&provider=github&intent=link-account
?social=error&code=access_denied&intent=authenticate
```

The frontend:

1. accepts only known status/provider/intent/code values;
2. removes callback parameters with `history.replaceState` while preserving unrelated query/hash state;
3. restores the Vote System session through the refresh cookie;
4. opens create-ballot only when successful callback intent is `create-ballot`;
5. leaves direct social login at authenticated Voter ID;
6. preserves an existing valid session on provider cancel/failure;
7. renders safe local error copy instead of reflecting provider text.

Social callback notices are separate from feed errors so a feed reload cannot erase authentication feedback.

## Explicit account linking

A signed-in user starts linking from Voter ID:

```http
POST /api/v1/auth/social/github/link/start
Authorization: Bearer <access-token>
```

The request uses the normal authorized lifecycle and receives one refresh/retry opportunity on 401. The backend captures the authenticated local user before redirecting to the provider. Link success returns through the normal refresh/profile restore path, after which Voter ID exposes the linked provider.

The frontend never links accounts by matching email.

## Expired access tokens

Authorized operations execute through `runAuthorizedRequest`:

1. Execute once with the current access token.
2. Only a 401 starts refresh.
3. Concurrent 401 responses share the single promise held by `authApi.refresh`.
4. Store the rotated access token.
5. Retry the original operation exactly once.
6. Failed refresh or a second 401 clears local session state.

Non-401 responses are not retried automatically. A 429 preserves `Retry-After` in `ApiError.retryAfter` so UI can communicate when retry is safe.

## Logout

`LOG OUT` calls `POST /api/v1/auth/logout` before clearing local state. `LOG OUT ALL DEVICES` calls authenticated `POST /api/v1/auth/logout-all`.

Local state is cleared in a `finally` block so network failure cannot leave a stale authenticated UI.

## Active production: Vercel + Railway

Current production is cross-origin but same-site:

```text
Frontend: https://app.ballotbox.io.vn
Backend:  https://api.ballotbox.io.vn
```

Required frontend build variable:

```env
NEXT_PUBLIC_API_BASE_URL=https://api.ballotbox.io.vn
```

Required backend settings:

```env
CORS_ALLOWED_ORIGINS=https://app.ballotbox.io.vn
REFRESH_COOKIE_SECURE=true
REFRESH_COOKIE_SAME_SITE=Lax
OAUTH_SESSION_COOKIE_SECURE=true
OAUTH_SESSION_COOKIE_SAME_SITE=Lax
SOCIAL_LOGIN_SUCCESS_URL=https://app.ballotbox.io.vn/
SOCIAL_LOGIN_FAILURE_URL=https://app.ballotbox.io.vn/
```

The frontend sends `credentials: include`. Spring Security returns the explicit origin and `Access-Control-Allow-Credentials: true`; wildcard origins are intentionally not used.

Because both hosts are HTTPS subdomains of the same registrable domain, `SameSite=Lax` is the current production choice. A future frontend/API split across different sites may require `SameSite=None` with `Secure=true` for both refresh and temporary OAuth cookies.

## Local development

For Next.js on `http://localhost:3000` and Spring Boot on `http://localhost:8080`:

Frontend:

```env
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
```

Backend:

```env
CORS_ALLOWED_ORIGINS=http://localhost:3000
REFRESH_COOKIE_SECURE=false
REFRESH_COOKIE_SAME_SITE=Lax
OAUTH_SESSION_COOKIE_SECURE=false
OAUTH_SESSION_COOKIE_SAME_SITE=Lax
SOCIAL_LOGIN_SUCCESS_URL=http://localhost:3000/
SOCIAL_LOGIN_FAILURE_URL=http://localhost:3000/
```

## Optional combined-origin deployment

The root production Dockerfile still packages the exported frontend into Spring Boot for CI/runtime smoke and optional single-service deployment.

For that topology:

```env
NEXT_PUBLIC_API_BASE_URL=
REFRESH_COOKIE_SECURE=true
REFRESH_COOKIE_SAME_SITE=Lax
OAUTH_SESSION_COOKIE_SECURE=true
OAUTH_SESSION_COOKIE_SAME_SITE=Lax
SOCIAL_LOGIN_SUCCESS_URL=https://vote.example.com/
SOCIAL_LOGIN_FAILURE_URL=https://vote.example.com/
```

This is not the active `ballotbox.io.vn` production topology.

## Request correlation and restore metrics

The backend exposes `X-Request-ID`. Restore logs and API completion logs use the same safe request ID when supplied. The frontend may surface a request ID in support/debug output but must never log access tokens, refresh cookies, provider tokens, or token hashes.

Important metrics:

```text
vote.auth.restore.total
vote.auth.restore.stage
vote.rate_limit.latency
vote.http.request.duration
http.server.requests
```

The restore pipeline times refresh-token hashing, locked session lookup, user lookup, rotation writes, JWT issuance, `/users/me` user lookup, and linked-identity loading with bounded tags.

## Verification

`npm test` compiles frontend API/auth modules and runs Node's built-in test runner. Current coverage includes:

- refresh single-flight behavior;
- concurrent 401 retry behavior;
- failed refresh clearing;
- one-time retry boundaries;
- backend logout calls;
- `credentials: include`, bearer headers, Problem Details, and `Retry-After`;
- provider discovery, social start, and authenticated linking;
- callback parsing, safe copy, URL cleanup, and create-ballot continuation;
- public feed access while restore is running;
- authenticated reconciliation after restore;
- protected MINE behavior;
- forwarding `AbortSignal` to ballot-list transport.

Browser QA verifies provider controls, mobile/touch behavior, create-ballot social continuation, provider cancellation with an existing session, linked-provider state, and the supported Ballot Edition viewport matrix.