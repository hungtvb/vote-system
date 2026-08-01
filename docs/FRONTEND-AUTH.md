# Frontend authentication lifecycle

## Runtime model

The browser keeps the short-lived Vote System access token in React memory only. The long-lived refresh token is stored by the backend in an `HttpOnly` cookie. Neither token is written to `localStorage` or `sessionStorage`.

Frontend API responsibilities:

- `transport.ts`: base URL, credentialed fetch, Problem Details normalization, `Retry-After`, and request headers;
- `auth-api.ts`: register, login, refresh, logout, and logout-all;
- `social-auth-api.ts`: provider discovery, social start, and authenticated link start;
- `authorized-request.ts`: one access-token retry after a 401;
- `session-bootstrap.ts`: session/profile validation;
- `useSession.ts`: shared React session/profile state, refresh single-flight, and local clearing;
- `user-api.ts` and `ballot-api.ts`: domain requests.

Google/GitHub provider tokens never enter the frontend lifecycle. Spring Security exchanges provider codes, creates the normal Vote System session, discards temporary OAuth state, and redirects with safe status metadata only.

## Single-request auth bootstrap

Register, login, and refresh return session fields plus the private profile. The refresh token remains cookie-only. The normal startup path does not call `/users/me` after refresh; a one-call compatibility fallback exists only for an older rolling-deploy response without `profile`.

On startup:

1. public LATEST/HOT/TOP requests start immediately;
2. refresh runs in parallel with `credentials: include`;
3. a successful response rotates the cookie and returns JWT plus profile;
4. the frontend validates `profile.id === userId` and commits both together;
5. the first public page reconciles in the background so `myVote` becomes authoritative.

`MINE` waits for an authenticated session. A failed guest restore does not block public content.

## Social authentication and linking

Enabled providers come from:

```http
GET /api/v1/auth/social/providers
```

Start requests use an allowlisted intent:

```http
POST /api/v1/auth/social/google/start
Content-Type: application/json

{"intent":"authenticate"}
```

or `create-ballot`. Linking uses the normal authorized lifecycle:

```http
POST /api/v1/auth/social/github/link/start
Authorization: Bearer <access-token>
```

Callback parameters contain only allowlisted status/provider/intent/code values. Safe examples:

```text
?social=success&provider=google&intent=create-ballot
?social=linked&provider=github&intent=link-account
?social=error&code=access_denied&intent=authenticate
?social=error&code=account_unavailable&intent=authenticate
```

`account_unavailable` maps to generic VI/EN copy. It does not reveal whether the account is suspended, banned, expired, missing, or holding a stale token, and it never reflects the administrator reason.

The frontend removes callback parameters with `history.replaceState`, restores the Vote System session only after successful callback, and preserves an existing valid session on provider cancellation/failure.

## Authorized request retry

`runAuthorizedRequest`:

1. executes with the current access token;
2. only a 401 starts refresh;
3. concurrent 401 responses share one refresh promise;
4. successful refresh commits the rotated JWT and profile together;
5. the original request retries exactly once;
6. failed refresh or a second 401 clears local session/profile state.

Non-401 responses are not automatically retried. A 429 or 503 preserves `Retry-After`.

## Server-owned token state

Each access JWT contains the current database role and a monotonic `security_version`. Every authenticated backend request compares both claims with PostgreSQL. Existing JWTs become invalid immediately after:

- single-session logout backed by an active refresh session;
- logout-all;
- administrator revoke-sessions;
- suspend, ban, or explicit restore;
- promotion to administrator.

A temporary restriction already invalidates tokens when the restriction is created. Its later expiry does not rotate the version again.

The frontend does not inspect or persist this version. It treats the resulting 401 through the standard one-refresh retry path. Because the same operation also revokes refresh sessions or changes the account version, refresh fails and local session/profile state is cleared.

## Restricted-account behavior

`ACTIVE`, `SUSPENDED`, and `BANNED` are backend-owned. The frontend does not infer or store account status.

When an account is restricted:

- all active refresh sessions are revoked atomically;
- all already-issued JWTs receive 401 on their next authenticated REST or SSE request;
- the normal retry attempts refresh once;
- refresh also returns 401;
- local session and private profile are cleared;
- public feeds remain usable anonymously;
- public profile and historical public content remain available under existing privacy rules.

A social callback for a restricted account redirects with `account_unavailable`, writes no refresh cookie, and does not commit a new provider link. The UI displays generic support guidance only.

The backend performs the authoritative PostgreSQL account lookup. A database failure does not allow the frontend to continue as authenticated.

Detailed backend contract: [`ACCOUNT-MODERATION.md`](ACCOUNT-MODERATION.md).

## Logout

`LOG OUT` calls `POST /api/v1/auth/logout`; `LOG OUT ALL DEVICES` calls authenticated `POST /api/v1/auth/logout-all`. Local state clears before the network request so network failure cannot leave a stale authenticated UI.

Single-session logout accepts the current host-only refresh cookie. When that refresh session is active, the backend revokes it and increments the account security version, so every already-issued access JWT becomes stale immediately. Other devices retain their refresh sessions and can transparently obtain a token with the new version; they are not logged out.

A missing, expired, or already-revoked cookie is cleared but cannot increment the security version. This prevents a stolen historical refresh token from repeatedly invalidating a user's current access tokens.

Logout-all and administrator `revoke-sessions` revoke all active refresh sessions and invalidate all existing access JWTs immediately. A later request with a retained token receives 401 rather than remaining valid until expiry.

## Cookie-origin boundary

Cookie-authenticated browser operations validate `Origin` and Fetch Metadata before session processing:

```text
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
POST /api/v1/auth/social/{provider}/start
POST /api/v1/auth/social/{provider}/link/start
```

An explicit Origin must match the configured frontend origin or the API origin produced by the framework-normalized request. Raw `X-Forwarded-Host` and `X-Forwarded-Proto` headers are never trusted by this filter.

A browser request without Origin is accepted only when Fetch Metadata reports `same-origin` or `none`. `same-site`, `cross-site`, and unknown browser contexts are rejected. Non-browser clients with neither Origin nor Fetch Metadata remain supported. Rejections return `403 SESSION_ORIGIN_REJECTED`.

This boundary complements `SameSite`, CORS, and bearer-token authorization.

## Deployment

Active production is same-site cross-origin:

```text
Frontend: https://app.ballotbox.io.vn
Backend:  https://api.ballotbox.io.vn
```

Frontend:

```env
NEXT_PUBLIC_API_BASE_URL=https://api.ballotbox.io.vn
```

Backend production requirements:

```env
CORS_ALLOWED_ORIGINS=https://app.ballotbox.io.vn
REFRESH_COOKIE_NAME=__Secure-vote_refresh
OAUTH_SESSION_COOKIE_NAME=__Secure-vote_oauth
SOCIAL_LOGIN_SUCCESS_URL=https://app.ballotbox.io.vn/
SOCIAL_LOGIN_FAILURE_URL=https://app.ballotbox.io.vn/
```

The production profile enforces secure refresh/OAuth cookies. Refresh uses `SameSite=Strict`; the OAuth session uses `SameSite=Lax` for provider redirects. Credentialed requests use `credentials: include`. Wildcard and non-canonical CORS origins are rejected at startup.

Local development uses `http://localhost:3000` and `http://localhost:8080` with the non-production profile and secure cookie flags disabled. The root combined Docker image remains an optional same-origin topology; CI explicitly runs it with the production profile and secure cookie settings.

## Request correlation and metrics

The backend exposes `X-Request-ID`. Frontend support output may surface a safe request ID but must never log access tokens, refresh cookies, provider tokens, or token hashes.

```text
vote.auth.restore.total
vote.auth.restore.stage
vote.rate_limit.latency
vote.http.request.duration
http.server.requests
```

## Verification

Frontend tests cover:

- refresh single-flight and one-time retry;
- bootstrap profile validation and compatibility fallback;
- failed refresh clearing;
- credentialed requests, Problem Details, and `Retry-After`;
- provider discovery/start/link and callback cleanup;
- safe `account_unavailable` copy without moderation-detail leakage;
- public feed access while restore runs;
- authenticated reconciliation, protected MINE, and abort forwarding.

Backend and focused security tests cover register/login/refresh bootstrap, rotation/replay/logout, active-session owner resolution, repeated revoked-cookie protection, role and security-version validation, forwarded-header and missing-Origin rejection, linked providers, immediate restricted-account enforcement, social callback rejection, session revocation, explicit restore, and expiry normalization without redundant version rotation.

See [`SECURITY-HARDENING.md`](SECURITY-HARDENING.md) for deployment kill-tests.
