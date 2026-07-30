# Admin foundation

This document covers the implemented administrator authorization, controlled bootstrap, immutable audit log, ballot moderation, and account moderation boundaries.

## Roles and authorization

Vote System accepts two roles:

```text
USER
ADMIN
```

Registration and social onboarding always create `USER`. Clients cannot request or update roles. Every `/api/v1/admin/**` route requires `ROLE_ADMIN` in the request chain, and admin controllers use `@PreAuthorize("hasRole('ADMIN')")` as defense in depth.

```text
anonymous request       -> 401 Unauthorized
authenticated USER      -> 403 Forbidden
authenticated ADMIN     -> evaluated by the admin operation
```

Role and account status are separate:

```text
Role            USER | ADMIN
AccountStatus   ACTIVE | SUSPENDED | BANNED
```

A valid ADMIN role does not bypass account-status enforcement. A suspended or banned administrator cannot use an old JWT, refresh, complete social login, or call admin APIs.

## Probe endpoint

```http
GET /api/v1/admin/probe
Authorization: Bearer <admin access token>
```

Successful response:

```json
{"status":"ok"}
```

The probe verifies request authorization, JWT role conversion, account-status enforcement, and method security. It is not a general infrastructure health endpoint.

## Controlled administrator bootstrap

Bootstrap is disabled by default:

```dotenv
ADMIN_BOOTSTRAP_ENABLED=false
ADMIN_BOOTSTRAP_EMAIL=
```

It promotes only an existing effective-`ACTIVE` account selected by normalized email. It never creates an account, password, token, refresh session, or provider identity. A missing, invalid, suspended, or banned target causes startup to fail without logging the configured email or credentials.

Procedure:

1. Register or sign in normally so the target exists as `USER` and is `ACTIVE`.
2. Set `ADMIN_BOOTSTRAP_EMAIL` and temporarily enable bootstrap.
3. Deploy once and obtain a fresh token showing `ADMIN`.
4. Disable bootstrap, clear the email, and deploy again.

Promotion is idempotent. Existing JWTs keep their original role claim until login or refresh issues a new token.

## Immutable audit log

`admin_audit_logs` is the append-only source for successful administrator mutations.

```text
id          UUID audit record ID
actorId     administrator user ID
action      controlled AdminAuditAction
targetType  POST, USER, or RANKING
targetId    bounded target identifier
reason      required administrator reason
metadata    bounded flat JSON object
createdAt   UTC instant
```

Implemented action vocabulary:

```text
ADMIN_HIDE_POST
ADMIN_RESTORE_POST
ADMIN_DELETE_POST
ADMIN_SUSPEND_USER
ADMIN_BAN_USER
ADMIN_RESTORE_USER
ADMIN_REVOKE_SESSIONS
ADMIN_REBUILD_RANKING
```

Action-to-target compatibility is enforced in Java and PostgreSQL. There is no public endpoint that accepts arbitrary audit events.

### Privacy and validation

Audit append:

- requires actor, action, target, and reason;
- limits reason to 500 characters and target ID to 128 characters;
- limits metadata to 20 flat string entries and 3 KiB serialized;
- rejects secret, password, token, authorization, cookie, credential, and email keys;
- rejects email-like, bearer-token, and control-character values;
- does not reflect rejected raw values in errors.

Metadata may contain previous/new machine states, restriction expiry, feed names, request IDs, and bounded counts. It must not contain email, display name, request bodies, cookies, provider payloads, tokens, password material, or secrets.

### Append-only enforcement

The application uses a Hibernate `@Immutable` entity, read-only repository surface, and insert-only store. PostgreSQL rejects every audit-row `UPDATE` and `DELETE` through a trigger. This protects against accidental mutation; it is not cryptographic tamper evidence against a database owner who can change schema or backups.

### Read API

```http
GET /api/v1/admin/audit-logs?page=0&size=20
Authorization: Bearer <admin access token>
```

Optional exact filters:

```text
action
actorId
targetType
targetId
```

Ordering is always `createdAt DESC, id DESC`. Page is zero-based and size is 1–100. No automatic deletion, expiry, export, or archival is implemented.

## Ballot moderation

Ballot lifecycle and moderation are independent:

```text
BallotStatus       OPEN | CLOSED
ModerationStatus   VISIBLE | HIDDEN | DELETED
```

```http
POST /api/v1/admin/posts/{postId}/hide
POST /api/v1/admin/posts/{postId}/restore
POST /api/v1/admin/posts/{postId}/delete
Authorization: Bearer <admin access token>
Content-Type: application/json

{"reason":"Violates published community rules"}
```

Transitions:

- `VISIBLE -> HIDDEN`;
- `HIDDEN -> VISIBLE`;
- `VISIBLE/HIDDEN -> DELETED`;
- `DELETED` is terminal in this phase.

State and audit append commit in one transaction under a ballot lock. Hidden/deleted ballots are excluded from public detail, feeds, vote, SSE, and owner mutations. Redis ranking and SSE subscriber changes happen only after commit. Administrator soft-delete preserves ballot and vote rows. See [`MODERATION.md`](MODERATION.md).

## Account moderation

```http
POST /api/v1/admin/users/{userId}/suspend
POST /api/v1/admin/users/{userId}/ban
POST /api/v1/admin/users/{userId}/restore
POST /api/v1/admin/users/{userId}/revoke-sessions
```

Suspend or ban:

```json
{
  "reason": "Repeated abuse after warning",
  "until": "2026-08-15T12:00:00Z"
}
```

`until` is optional. Null means permanent; temporary restrictions may be at most 365 days. Restore and revoke-session requests contain only `reason`.

Restriction and audit append commit atomically. Every active refresh session is revoked in the same transaction. PostgreSQL remains authoritative for local/social login, refresh, and every request carrying a JWT. An expired restriction is treated as `ACTIVE` without a scheduler.

Safeguards:

- administrators cannot suspend, ban, or revoke sessions for their own current account;
- the last effective active administrator cannot be restricted;
- a PostgreSQL advisory transaction lock serializes concurrent administrator-count decisions;
- refresh and moderation use `refresh sessions -> user` lock order to avoid deadlocks;
- repeated/incompatible actions return `409` without duplicate audit rows.

Public profile and historical content remain available anonymously. Detailed contract: [`ACCOUNT-MODERATION.md`](ACCOUNT-MODERATION.md).

## Verification

Backend coverage includes:

- anonymous `401`, USER `403`, ADMIN access, and direct method-security rejection;
- bootstrap normalization, idempotency, missing target, invalid configuration, and restricted-target rejection;
- audit privacy, JSONB persistence, filtering, deterministic ordering, and append-only trigger;
- ballot transitions, public visibility, stale Redis filtering, rollback, concurrency, and SSE cleanup;
- account suspend, ban, restore, explicit session revocation, temporary expiry, and public-history behavior;
- old JWT, login, refresh, authenticated write, and authenticated SSE rejection;
- social callback rejection without refresh-cookie issuance;
- self-lockout and last-active-admin protection;
- concurrent cross-restriction and refresh-vs-suspension convergence;
- rollback of account state and session revocation when audit validation fails.
