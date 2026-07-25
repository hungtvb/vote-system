# Frontend authentication lifecycle

## Runtime model

The browser keeps the short-lived access token in React memory only. The long-lived refresh token is stored by the backend in an `HttpOnly` cookie, so frontend JavaScript never reads or persists it.

The frontend API layer is split by responsibility:

- `transport.ts`: fetch configuration, problem-response normalization, typed `ApiError`, and `Retry-After` parsing.
- `auth-api.ts`: register, login, refresh, logout, logout-all, and the single-flight refresh lock.
- `user-api.ts`: current Voter ID profile.
- `ballot-api.ts`: ballot CRUD and voting endpoints.
- `authorized-request.ts`: one access-token retry after a 401 response.
- `useSession.ts`: React session/profile state, restore, backend logout, and session clearing.

## Startup restore

1. The application calls `POST /api/v1/auth/refresh` with `credentials: include`.
2. The backend validates and rotates the refresh cookie.
3. The frontend uses the returned access token to call `GET /api/v1/users/me`.
4. The UI enters the authenticated state only after both calls succeed.
5. A 401 or 403 during restore produces a clean guest state. Other errors are logged and also leave the UI signed out.

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
```

`NEXT_PUBLIC_API_BASE_URL` can remain empty because API requests use relative paths.

## Split-origin local development

For Next.js on `http://localhost:3000` and Spring Boot on `http://localhost:8080`:

```env
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
CORS_ALLOWED_ORIGINS=http://localhost:3000
REFRESH_COOKIE_SECURE=false
REFRESH_COOKIE_SAME_SITE=Lax
```

The frontend always sends `credentials: include`. Spring Security returns an explicit origin and `Access-Control-Allow-Credentials: true`; wildcard origins are intentionally not used.

## Cross-site HTTPS deployment

When the frontend and API are on different sites, browsers require:

```env
CORS_ALLOWED_ORIGINS=https://app.example.com
REFRESH_COOKIE_SECURE=true
REFRESH_COOKIE_SAME_SITE=None
NEXT_PUBLIC_API_BASE_URL=https://api.example.com
```

Both sites must use HTTPS. Add every allowed frontend origin explicitly as a comma-separated value. Do not use `*` with credentialed requests.

## Verification

`npm test` compiles the API modules with the repository TypeScript compiler and runs Node's built-in test runner. It covers:

- concurrent refresh single-flight behavior;
- concurrent 401 retry behavior;
- failed refresh session clearing;
- one-time retry boundaries;
- backend logout calls;
- `credentials: include` and bearer headers;
- problem response and `Retry-After` normalization.

The backend integration suite verifies configured credentialed preflight requests and rejects origins outside the allowlist.
