# Account moderation and access enforcement

TON-196 adds administrator-controlled account restrictions on top of the role and immutable audit foundations.

## Separate concerns

```text
Role            USER | ADMIN
AccountStatus   ACTIVE | SUSPENDED | BANNED
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

An expired restriction is treated as `ACTIVE` by the centralized access policy. No scheduler is required for correctness. A later moderation mutation may normalize the stored row back to `ACTIVE`.

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

1. locks active refresh sessions for the target;
2. locks the user row;
3. validates transition and administrator safeguards;
4. changes account state;
5. revokes every active refresh session;
6. appends the immutable audit record;
7. commits all changes together.

If validation or audit append fails, both account state and session revocation roll back.

Audit actions:

```text
ADMIN_SUSPEND_USER
ADMIN_BAN_USER
ADMIN_RESTORE_USER
ADMIN_REVOKE_SESSIONS
```

Metadata is bounded and contains only machine-safe state, expiry, and revoked-session counts. Email, display name, cookies, tokens, provider payloads, and request bodies are excluded.

## Immediate enforcement

PostgreSQL is authoritative. The backend performs an account-status lookup for every request carrying an authenticated Vote System JWT, after bearer-token authentication and before rate limiting or controller execution.

A non-active account receives `401` for:

- local login;
- social-login completion and account-link completion;
- refresh rotation;
- authenticated REST mutations and reads;
- authenticated SSE requests made with an already-issued access token.

Restriction also revokes all active refresh sessions in the same transaction. Already-issued access tokens are rejected immediately by the request filter instead of waiting for their normal expiry.

There is no account-status cache in this phase. This adds one PostgreSQL lookup per authenticated JWT request but guarantees immediate cross-instance enforcement. Database lookup failure does not fail open.

## Public history

Restriction does not erase historical content. Public profiles, ballot author summaries, and visible historical ballots remain readable anonymously under their existing privacy rules.

A restricted browser that sends its stale bearer token is rejected. Public browsing remains available after the client clears the invalid session and makes the request anonymously.

## Administrator safeguards

- An administrator cannot suspend, ban, or revoke sessions for their own current account.
- The last effective `ACTIVE` administrator cannot be restricted.
- A PostgreSQL advisory transaction lock serializes administrator-count decisions, including cross-restriction races between two administrators.
- Refresh rotation and moderation use the same `refresh sessions -> user` lock order to prevent deadlocks.
- Repeated or incompatible state transitions return `409` and do not append duplicate audit rows.

## Session revocation only

`revoke-sessions` leaves account status and role unchanged. Existing short-lived access JWTs remain valid until normal expiry; all current refresh sessions become unusable immediately. Repeating the operation when no active refresh session exists returns `409`.

## Rollout and rollback

Flyway V10 is additive and safe for the previous backend to read, but a pre-TON-196 backend does not enforce `account_status`.

Deployment order:

1. merge and deploy the TON-195 dependency;
2. deploy the TON-196 backend and confirm Flyway V10 plus health;
3. verify one controlled suspend/restore smoke with a non-admin test account;
4. then allow routine administrator use of account restrictions.

After any account becomes `SUSPENDED` or `BANNED`, do not roll back to a pre-TON-196 backend while serving authenticated traffic. That rollback would leave restriction rows in PostgreSQL but stop enforcing them. Prefer roll-forward. An emergency rollback requires maintenance mode or equivalent traffic blocking until the enforcement-capable backend is restored.

The frontend change is backward-compatible: it only adds safe copy for the new `account_unavailable` callback code.

## Verification

Integration coverage includes:

- anonymous `401` and USER `403` admin boundaries;
- old JWT, login, refresh, write, and SSE rejection after restriction;
- public-profile availability;
- restore behavior without role mutation;
- permanent ban and temporary expiry;
- explicit session revocation;
- self-lockout prevention;
- concurrent administrator cross-restriction;
- last-active-administrator protection;
- audit-failure rollback;
- social callback rejection without refresh-cookie issuance;
- refresh/moderation lock-order convergence.
