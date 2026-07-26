# Frontend authentication lifecycle

## Runtime model

The browser keeps the short-lived Vote System access token in React memory only. The long-lived Vote System refresh token is stored by the backend in an `HttpOnly` cookie, so frontend JavaScript never reads or persists it.

The frontend API layer is split by responsibility:

- `transport.ts`: fetch configuration, problem-response normalization, typed `ApiError`, and `Retry-After` parsing.
- `auth-api.ts`: register, login, refresh, logout, logout-all, and the single-flight refresh lock.
- `social-auth-api.ts`: enabled-provider discovery, social authorization start, and authenticated account-link start.
- `user-api.ts`: current Voter ID profile and linked-provider state.
- `ballot-api.ts`: ballot CRUD and voting endpoints.
- `authorized-request.ts`: one access-token retry after a 401 response.
- `useSession.ts`: React session/profile state, restore, backend logout, and session clearing.

Google/GitHub provider tokens never enter this frontend lifecycle. Spring Security performs the authorization-code exchange on the backend, creates the normal Vote System refresh session, discards the temporary provider session, and redirects back with only safe status metadata.

## Startup restore

1. The application calls `POST /api/v1/auth/refresh` with `credentials: include`.
2. The backend validates and rotates the Vote System refresh cookie.
3. The frontend uses the returned access token to call `GET /api/v1/users/me`.
4. The UI enters the authenticated state only after both calls succeed.
5. A 401 or 403 during restore produces a clean guest state. Other errors are logged and also leave the UI signed out.

The same restore path is used after a Google/GitHub callback. Social login does not create a second frontend session type.

## Enabled social providers

On startup the app calls:

```http
GET /api/v1/auth/social/providers
```

Only configured providers are rendered. When the backend returns an empty provider list, the auth dialog contains only email/password login and registration. A provider already linked to the current profile remains visible as `LINKED`; a missing provider receives a link action only when that provider is enabled on the backend.

## Starting social authentication

The auth dialog posts an allowlisted intent to the backend:

```http
POST /api/v1/auth/social/google/start
Content-Type: application/json

{"intent":"authenticate"}
```

or:

```json
{"intent":"create-ballot"}
```

The backend returns its own `/oauth2/authorization/{provider}` URL. The frontend never submits a return URL, callback URL, provider token, email, or account identifier.

The browser navigates away with `window.location.assign`. If the start request fails, the dialog remains usable and shows the backend problem detail; a valid existing Vote System session is not cleared.

## Social callback continuation

The configured backend success/failure redirect contains only allowlisted parameters:

```text
?social=success&provider=google&intent=create-ballot
?social=linked&provider=github&intent=link-account
?social=error&code=access_denied&intent=authenticate
```

The frontend:

1. parses only known status/provider/intent/code values;
2. removes the callback parameters with `history.replaceState` while preserving unrelated query parameters and the hash;
3. restores the Vote System session through the refresh cookie;
4. opens the create-ballot form only when a successful callback carries `create-ballot`;
5. leaves direct social login at the authenticated Voter ID state;
6. preserves an existing valid session on provider cancel or callback failure;
7. displays safe, local error copy rather than reflecting provider text.

Social callback notices are stored separately from feed-loading errors so a registry reload cannot erase the authentication result.

## Explicit account linking

A signed-in user starts provider linking from Voter ID:

```http
POST /api/v1/auth/social/github/link/start
Authorization: Bearer <access-token>
```

The call runs through the normal authorized-request lifecycle and therefore receives one refresh/retry opportunity on a 401. The authenticated local user ID is captured by the backend before the OAuth redirect. Link success causes a full redirect and normal refresh restore; the updated profile then exposes the linked provider.

The frontend does not attempt to link accounts by matching email.

## Expired access tokens

Authorized operations are executed through `runAuthorizedRequest`:

1. Execute once with the current access token.
2. Only a 401 starts refresh.
3. Concurrent 401 responses share the one promise held by `authApi.refresh`; they do not create multiple refresh requests.
4. Store the rotated access token.
5. Retry the original operation exactly once.
6. A failed refresh, or a second 401 after retry, clears the local session.

Non-401 responses are never retried automatically. In particular, a 429 response preserves the backend `Retry-After` value in `ApiError.retryAfter` for the UI.

## Logout

`LOG OUT` calls `POST /api/v1/auth/logout` before clearing local state. `LOG OUT ALL DEVICES` calls the authenticated `POST /api/v1/auth/logout-all`. Local state is cleared in a `finally` block so a network failure cannot leave a stale authenticated UI.

## Same-origin production

The production Docker image packages the exported Next.js frontend inside Spring Boot. Browser and API therefore share one origin. Recommended production settings are:

```env
REFRESH_COOKIE_SECURE=true
REFRESH_COOKIE_SAME_SITE=Lax
OAUTH_SESSION_COOKIE_SECURE=true
OAUTH_SESSION_COOKIE_SAME_SITE=Lax
SOCIAL_LOGIN_SUCCESS_URL=https://vote.example.com/
SOCIAL_LOGIN_FAILURE_URL=https://vote.example.com/
```

`NEXT_PUBLIC_API_BASE_URL` can remain empty because API requests use relative paths.

## Split-origin local development

For Next.js on `http://localhost:3000` and Spring Boot on `http://localhost:8080`:

```env
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
CORS_ALLOWED_ORIGINS=http://localhost:3000
REFRESH_COOKIE_SECURE=false
REFRESH_COOKIE_SAME_SITE=Lax
OAUTH_SESSION_COOKIE_SECURE=false
OAUTH_SESSION_COOKIE_SAME_SITE=Lax
SOCIAL_LOGIN_SUCCESS_URL=http://localhost:3000/
SOCIAL_LOGIN_FAILURE_URL=http://localhost:3000/
```

The frontend always sends `credentials: include`. Spring Security returns an explicit origin and `Access-Control-Allow-Credentials: true`; wildcard origins are intentionally not used.

## Cross-site HTTPS deployment

When the frontend and API are on different sites, browsers may require:

```env
CORS_ALLOWED_ORIGINS=https://app.example.com
REFRESH_COOKIE_SECURE=true
REFRESH_COOKIE_SAME_SITE=None
OAUTH_SESSION_COOKIE_SECURE=true
OAUTH_SESSION_COOKIE_SAME_SITE=None
SOCIAL_LOGIN_SUCCESS_URL=https://app.example.com/
SOCIAL_LOGIN_FAILURE_URL=https://app.example.com/
NEXT_PUBLIC_API_BASE_URL=https://api.example.com
```

Both sites must use HTTPS. Add every allowed frontend origin explicitly as a comma-separated value. Do not use `*` with credentialed requests.

## Verification

`npm test` compiles the API/auth modules with the repository TypeScript compiler and runs Node's built-in test runner. It covers:

- concurrent refresh single-flight behavior;
- concurrent 401 retry behavior;
- failed refresh session clearing;
- one-time retry boundaries;
- backend logout calls;
- `credentials: include` and bearer headers;
- problem response and `Retry-After` normalization;
- provider discovery, social start, and authenticated link requests;
- callback parsing, safe error copy, URL cleanup, and create-ballot continuation.

The browser QA matrix verifies configured provider buttons, mobile/touch layout, successful social create continuation, provider cancellation with an existing session, and linked-provider profile state.

The backend integration suite verifies configured credentialed preflight requests, rejects origins outside the allowlist, validates OAuth state/Google nonce generation, resolves durable provider subjects, enforces explicit linking, and issues the normal Vote System refresh cookie.
