# ADR 0002: JWT access tokens with rotating opaque refresh tokens

- **Status:** Accepted
- **Date:** 2026-07-24

## Context

The API needs stateless request authentication while still supporting long-lived browser sessions, logout, session revocation, and refresh-token theft detection.

Persisting raw refresh tokens would increase the impact of a database leak. Long-lived JWT access tokens would make revocation slow and difficult.

## Decision

Use short-lived HS256 JWT access tokens and opaque cryptographically random refresh tokens.

- Access tokens are sent as bearer tokens and validated statelessly.
- Refresh tokens are sent through `HttpOnly` cookies.
- PostgreSQL stores only SHA-256 refresh-token hashes and session metadata.
- Every successful refresh rotates the refresh token.
- Reuse of a rotated token is treated as replay and revokes all active refresh sessions for the affected user.
- Passwords are stored using BCrypt with strength 12.

## Consequences

### Positive

- Normal API requests require no session database lookup.
- Database disclosure does not directly reveal usable refresh tokens.
- Rotation limits the useful lifetime of a stolen refresh token.
- Logout and logout-all can revoke refresh sessions.

### Negative

- An issued access token remains valid until its short expiry.
- Replay handling can force a user to authenticate again on every device.
- Secret rotation invalidates existing access tokens unless overlapping keys are introduced later.

## Operational requirements

- `JWT_SECRET` must contain at least 32 high-entropy bytes.
- Refresh cookies must be `Secure` in production.
- Authentication events should be logged without recording raw tokens.
- Token TTLs and cookie policy must be reviewed before exposing the service across multiple origins.