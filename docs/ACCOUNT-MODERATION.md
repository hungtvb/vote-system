# Account moderation and access enforcement

TON-196 adds administrator-controlled account restrictions on top of the role and immutable audit foundations. The August 2026 hardening pass extends the same boundary to immediate access-token revocation.

## Separate concerns

```text
Role            USER | ADMIN
AccountStatus   ACTIVE | SUSPENDED | BANNED
securityVersion monotonic access-token revocation version
```

Role controls authorization. Account status controls whether an account may authenticate or use an authenticated session. Restriction never changes the account role, profile, ballots, votes, or linked provider identities.

## Status and expiry

```text
ACTIVE       authenticated access allowed
SUSPENDED    authenticated access denied
BANNED       authenticated access denied
```

`statusUntil` is optional:

- omitted or `null`: permanent until an administrator restores the account;
- future timestamp: temporary restriction;
- maximum temporary duration: 365 days.

An expired restriction is treated as `ACTIVE` by the centralized access policy. No scheduler is required for correctness. A later moderation mutation may normalize the stored row back to `ACTIVE`. The restriction already rotated the security version when it was created, so expiry normalization does not rotate it again.

## Administrator API

All endpoints require `ROLE_ADMIN` at request and method levels.

```http
POST /api/v1/admin/users/{userId}/suspend
POST /api/v1/admin/users/{userId}/ban
POST /api/v1/admin/users/{userId}/restore
POST /api/v1/admin/users/{userId}/revoke-sessions
```

Suspend or ban request:

```json
{
  "reason": "Repeated abuse after warning",
  "until": "2026-08-15T12:00:00Z"
}
```

`until` may be omitted for a permanent restriction. Restore and session-revocation requests contain only the required reason:

```json
{
  "reason": "Appeal accepted"
}
```

Reason is trimmed, required, and limited to 500 characters.

## Atomic mutation and audit

A restriction transaction:

1. locks the user row;
2. locks active refresh sessions for the target;
3. validates transition and administrator safeguards;
4. changes account state and increments `securityVersion`;
5. revokes every active refresh session;
6. appends the immutable audit record;
7. commits all changes together.

If validation or audit append fails, account state, access-token revocation, and refresh-session revocation all roll back.

Audit actions:

```text
ADMIN_SUSPEND_USER
ADMIN_BAN_USER
ADMIN_RESTORE_USER
ADMIN_REVOKE_SESSIONS
```

Metadata is bounded and contains only machine-safe state, expiry, revoked-session counts, and whether access tokens were revoked. Email, display name, cookies, tokens, provider payloads, and request bodies are excluded.

## Immediate enforcement

PostgreSQL is authoritative. The backend performs an account lookup for every request carrying an authenticated Vote System JWT, after bearer-token authentication and before rate limiting or controller execution.

Each access JWT contains the account role and `security_version`. The request filter requires:

1. an existing active account;
2. a token version equal to the current database version;
3. exactly one token role equal to the current database role.

A mismatch receives `401`. This rejects stale tokens after logout-all, explicit administrator revocation, account moderation, and role promotion.

A non-active account receives `401` for:

- local login;
- social-login completion and account-link completion;
- refresh rotation;
- authenticated REST mutations and reads;
- authenticated SSE requests made with an already-issued access token.

Restriction revokes all active refresh sessions and existing access tokens in the same transaction. There is no account-status or security-version cache. This adds one PostgreSQL lookup per authenticated JWT request but guarantees immediate cross-instance enforcement. Database lookup failure does not fail open.

## Public history

Restriction does not erase historical content. Public profiles, ballot author summaries, and visible historical ballots remain readable anonymously under their existing privacy rules.

A restricted browser that sends its stale bearer token is rejected. Public browsing remains available after the client clears the invalid session and makes the request anonymously.

## Administrator safeguards

- An administrator cannot suspend, ban, or revoke sessions for their own current account.
- The last effective `ACTIVE` administrator cannot be restricted.
- A PostgreSQL advisory transaction lock serializes administrator-count decisions, including cross-restriction races between two administrators.
- Refresh rotation, user-driven session revocation, and moderation use the same `user -> refresh sessions` lock order to prevent deadlocks and stale-session resurrection.
- Repeated or incompatible state transitions return `409` and do not append duplicate audit rows.

## Session revocation only

`revoke-sessions` leaves account status and role unchanged. It revokes every active refresh session and increments `securityVersion`, so all already-issued access JWTs are rejected immediately. The operation remains meaningful even when no active refresh session exists because access tokens may still be outstanding; it therefore succeeds with a revoked-session count of zero instead of returning `409`.

`logout-all` uses the same rule for the authenticated user: refresh sessions are revoked and all previously issued access JWTs become stale.

## Rollout and rollback

Flyway V10 introduced account moderation. Flyway V14 adds the non-null `users.security_version` column with default zero.

Deployment order:

1. deploy the backend that contains V14 and token-version validation;
2. confirm Flyway and Hibernate validation succeed;
3. obtain a fresh ADMIN token;
4. verify logout-all and administrator revoke-sessions invalidate a retained access JWT immediately;
5. verify a controlled suspend/restore smoke with a non-admin test account.

Tokens issued by an older backend without `security_version` are accepted only while the database version is zero, which supports one rolling-deployment window. Once an account version changes, only a newly issued token is valid.

After V14 is active and any security version has changed, do not roll back to a backend that does not validate the version while authenticated traffic is enabled. Prefer roll-forward. An emergency rollback requires maintenance mode or equivalent traffic blocking until the enforcement-capable backend is restored.

## Verification

Integration and focused security coverage include:

- anonymous `401` and USER `403` admin boundaries;
- forged role and stale security-version rejection;
- old JWT, login, refresh, write, and SSE rejection after restriction;
- immediate access-token rejection after logout-all and administrator revoke-sessions;
- public-profile availability;
- restore behavior without role mutation;
- permanent ban and temporary expiry without redundant version rotation;
- self-lockout prevention;
- concurrent administrator cross-restriction;
- last-active-administrator protection;
- audit-failure rollback;
- social callback rejection without refresh-cookie issuance;
- refresh/moderation lock-order convergence.

See [`SECURITY-HARDENING.md`](SECURITY-HARDENING.md) for the production kill-tests and rollout boundary.
