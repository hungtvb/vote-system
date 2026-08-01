# Security hardening and production verification

This document describes the security boundaries introduced by the August 2026 hardening pass and the checks required before production rollout.

## Production startup boundary

The `production` Spring profile fails startup unless all security-critical configuration is explicit and safe:

- `JWT_SECRET` is at least 32 bytes, is not a committed placeholder, and has reasonable character diversity;
- `CORS_ALLOWED_ORIGINS` contains only canonical explicit HTTPS origins, with no wildcard, path, query, credentials, localhost, or loopback entry;
- the refresh cookie is `Secure`, uses `SameSite=Strict` or `Lax`, and has a `__Secure-` name;
- the OAuth session cookie is `Secure` and has a `__Secure-` name.

Recommended Railway values:

```dotenv
JWT_SECRET=<random-production-secret>
JWT_ISSUER=vote-system
CORS_ALLOWED_ORIGINS=https://app.ballotbox.io.vn
REFRESH_COOKIE_NAME=__Secure-vote_refresh
OAUTH_SESSION_COOKIE_NAME=__Secure-vote_oauth
```

The production profile enforces the Secure and SameSite attributes. A deployment that boots with a development fallback is considered failed rather than degraded.

## Immediate access-token revocation

Every user row contains a monotonic `security_version`. Access JWTs contain the version and the current role. For every authenticated request the backend loads the user from PostgreSQL and verifies:

1. the account is active;
2. the JWT security version equals the database value;
3. the JWT contains exactly one role and it equals the database role.

The version increments on:

- logout with an active refresh session;
- logout-all;
- administrator session revocation;
- suspend or ban;
- explicit restore;
- promotion to administrator.

A temporary restriction already rotates the version when it is created. Expiry normalization only cleans the stored status and does not rotate the version a second time.

Single logout resolves the account only from an active refresh session. An expired, missing, or already-revoked refresh token cannot increment the version again, preventing repeated invalidation abuse. Other devices keep their refresh sessions and can obtain a token with the new version.

This makes previously issued JWTs unusable immediately. Refresh-session rotation and replay detection continue to operate independently.

## Cookie-authenticated browser operations

CSRF is not enabled for bearer-token APIs. Cookie-authenticated session endpoints instead enforce browser origin boundaries:

```text
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
POST /api/v1/auth/social/{provider}/start
POST /api/v1/auth/social/{provider}/link/start
```

Every request that carries an `Origin` header must match the explicit `CORS_ALLOWED_ORIGINS` allow-list exactly. The filter does not derive trust from request host, proxy metadata, `X-Forwarded-Host`, or `X-Forwarded-Proto`. A same-origin browser deployment must therefore include its own origin explicitly in the allow-list.

Browser requests without Origin are allowed only for `Sec-Fetch-Site: same-origin` or `none`. `same-site`, `cross-site`, and unknown browser contexts are rejected. Non-browser clients without Origin and Fetch Metadata remain supported.

Rejected browser requests return `403` with code `SESSION_ORIGIN_REJECTED`.

## Rate-limit failure policy

Redis remains the shared sliding-window source. Failure behavior is rule-specific:

- login, registration, refresh, social authentication, and social linking fail closed;
- non-authentication operations may follow `RATE_LIMIT_FAIL_OPEN`.

When an authentication rule cannot be verified, the API returns `503`, code `RATE_LIMIT_UNAVAILABLE`, and `Retry-After: 5`.

## Protected operational endpoints

Public:

```text
/actuator/health/**
```

Administrator only:

```text
/actuator/** except health
/api/v1/admin/**
```

This deny-by-default matcher also protects newly exposed Actuator endpoints. Springdoc remains disabled in the production profile.

## Browser hardening

Vercel responses include:

- Content Security Policy;
- HSTS;
- frame denial;
- `nosniff`;
- strict-origin referrer policy;
- restrictive permissions policy;
- cross-origin opener policy;
- `X-Robots-Tag` for admin paths.

The static frontend never acts as the authorization boundary. Non-admin visits to `/admin` resolve to neutral not-found UI and do not call administrator APIs; the backend still enforces `ROLE_ADMIN` and current token state.

## Supply-chain and CI controls

- Spring Boot is maintained on the latest supported 3.5 patch used by the project;
- Dependabot covers Maven, npm, GitHub Actions, and Docker;
- OSV Scanner checks dependencies on pull requests, main, and a weekly schedule;
- GitHub CodeQL Default Setup analyzes the repository without a conflicting advanced workflow;
- the combined Docker runtime smoke explicitly activates the production profile;
- runtime-smoke artifacts are scanned to ensure they contain no refresh cookie or JWT.

The runtime smoke uses an ephemeral random JWT secret and temporary response-header storage outside the uploaded artifact directory.

## Deployment kill-tests

Run these after deploying the backend and frontend:

1. Confirm `/v3/api-docs` and `/swagger-ui/index.html` are unavailable.
2. Confirm anonymous and USER tokens cannot read `/actuator`, `/actuator/info`, or `/actuator/metrics`.
3. Login, retain the access JWT, call logout, and confirm the retained JWT receives `401`; confirm another device can refresh and remain signed in.
4. Login again, retain the access JWT, call logout-all, and confirm the retained JWT receives `401` and every refresh session fails.
5. Revoke a user's sessions from admin and confirm their retained JWT receives `401`, even when no refresh session existed.
6. Send refresh with `Origin: https://attacker.example` plus forged forwarded headers and confirm `403 SESSION_ORIGIN_REJECTED`.
7. Send refresh with `Origin: https://api.ballotbox.io.vn` while only `https://app.ballotbox.io.vn` is allow-listed; confirm `403 SESSION_ORIGIN_REJECTED`.
8. Send a browser-style refresh with no Origin and `Sec-Fetch-Site: same-site`; confirm `403 SESSION_ORIGIN_REJECTED`.
9. Stop or isolate Redis and confirm login returns `503 RATE_LIMIT_UNAVAILABLE` rather than bypassing the limiter.
10. Verify refresh and OAuth cookies have the expected `__Secure-` names, `HttpOnly`, `Secure`, and SameSite attributes.
11. Verify Vercel returns CSP, HSTS, frame, referrer, permissions, and content-type headers.
12. Confirm a guest and a signed-in USER visiting `/admin` reach neutral 404 behavior and produce no `/api/v1/admin/**` requests.
13. Confirm an ADMIN can still recover the system from maintenance mode.

## Rollout and rollback

Flyway V14 adds `users.security_version` with default zero. Existing access tokens without the claim are treated as version zero for one rolling-deployment window. Any user whose version changes must use a newly issued token.

Changing the production refresh-cookie name from `vote_refresh` to `__Secure-vote_refresh` intentionally invalidates existing browser refresh cookies. Plan the rollout as a one-time reauthentication event and communicate it before deployment.

After V14 is active and versions have changed, do not roll back to a backend that does not validate `security_version` while authenticated traffic is enabled. Prefer roll-forward; otherwise enter maintenance mode during rollback.
