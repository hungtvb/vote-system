# Security hardening and production verification

This document describes the security boundaries introduced by the August 2026 hardening pass and the checks required before production rollout.

## Production startup boundary

The `production` Spring profile fails startup unless all security-critical configuration is explicit and safe:

- `JWT_SECRET` is at least 32 bytes, is not a committed placeholder, and has reasonable character diversity;
- `CORS_ALLOWED_ORIGINS` contains only explicit HTTPS origins, with no wildcard or localhost entry;
- the refresh cookie is `Secure`, uses `SameSite=Strict` or `Lax`, and has a `__Secure-` name;
- the OAuth session cookie is `Secure` and has a `__Secure-` name.

Recommended Railway values:

```dotenv
JWT_SECRET=<cryptographically-random-secret>
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

- logout-all;
- administrator session revocation;
- suspend or ban;
- restore;
- expiry normalization of a temporary restriction;
- promotion to administrator.

This makes previously issued JWTs unusable immediately. Refresh-session rotation and replay detection continue to operate independently.

## Cookie-authenticated browser operations

CSRF is not enabled for bearer-token APIs. Cookie-authenticated session endpoints instead enforce browser origin boundaries:

```text
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
POST /api/v1/auth/social/{provider}/start
POST /api/v1/auth/social/{provider}/link/start
```

Requests with an `Origin` header must match the explicit CORS allow-list or the API's own origin. Requests without Origin but marked `Sec-Fetch-Site: cross-site` are rejected. Non-browser clients without either header remain supported.

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
/actuator/info
/actuator/metrics
/actuator/metrics/**
/api/v1/admin/**
```

Springdoc remains disabled in the production profile.

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

## Supply-chain controls

- Spring Boot is maintained on the latest supported 3.5 patch used by the project;
- Dependabot covers Maven, npm, GitHub Actions, and Docker;
- dependency review blocks newly introduced high-severity vulnerable dependencies;
- CodeQL analyzes Java/Kotlin and JavaScript/TypeScript.

## Deployment kill-tests

Run these after deploying the backend and frontend:

1. Confirm `/v3/api-docs` and `/swagger-ui/index.html` are unavailable.
2. Confirm anonymous and USER tokens cannot read `/actuator/metrics`.
3. Login, retain the access JWT, call logout-all, and confirm the retained JWT receives `401`.
4. Revoke a user's sessions from admin and confirm their retained JWT receives `401`, even when no refresh session existed.
5. Send refresh with `Origin: https://attacker.example` and confirm `403 SESSION_ORIGIN_REJECTED`.
6. Stop or isolate Redis and confirm login returns `503 RATE_LIMIT_UNAVAILABLE` rather than bypassing the limiter.
7. Verify refresh and OAuth cookies have the expected `__Secure-` names, `HttpOnly`, `Secure`, and SameSite attributes.
8. Verify Vercel returns CSP, HSTS, frame, referrer, permissions, and content-type headers.
9. Confirm a guest and a signed-in USER visiting `/admin` reach neutral 404 behavior and produce no `/api/v1/admin/**` requests.
10. Confirm an ADMIN can still recover the system from maintenance mode.

## Rollout and rollback

Flyway V14 adds `users.security_version` with default zero. Existing access tokens without the claim are treated as version zero for one rolling-deployment window. Any user whose version changes must use a newly issued token.

After V14 is active and versions have changed, do not roll back to a backend that does not validate `security_version` while authenticated traffic is enabled. Prefer roll-forward; otherwise enter maintenance mode during rollback.
