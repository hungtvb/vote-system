# Admin foundation

This document covers the authorization boundary and controlled bootstrap introduced by TON-192, the immutable audit-log foundation introduced by TON-194, and the audited ballot-moderation boundary introduced by TON-195. User moderation, operational controls, and the protected admin dashboard remain later phases of TON-109.

## Roles and authorization

Vote System accepts two application roles:

```text
USER
ADMIN
```

Registration and social onboarding always create `USER` accounts. Clients cannot request a role during registration or profile updates.

Every route under `/api/v1/admin/**` requires `ROLE_ADMIN` in the Spring Security request chain. Admin controller methods also use `@PreAuthorize("hasRole('ADMIN')")` as defense in depth.

Expected boundary behavior:

```text
anonymous request       -> 401 Unauthorized
authenticated USER      -> 403 Forbidden
authenticated ADMIN     -> allowed by the admin boundary
```

Access tokens carry a `roles` claim. The resource server maps each value to a Spring Security `ROLE_*` authority.

## Probe endpoint

```http
GET /api/v1/admin/probe
Authorization: Bearer <admin access token>
```

Successful response:

```json
{
  "status": "ok"
}
```

The probe verifies the request-level matcher, JWT role conversion, and method-level guard. It is not a general health endpoint and does not expose infrastructure details.

## Controlled administrator bootstrap

Bootstrap is disabled by default:

```dotenv
ADMIN_BOOTSTRAP_ENABLED=false
ADMIN_BOOTSTRAP_EMAIL=
```

It only promotes an existing account selected by normalized email. It never creates an account, password, refresh token, or OAuth identity.

### Promotion procedure

1. Register or sign in normally so the target account already exists as `USER`.
2. Set `ADMIN_BOOTSTRAP_EMAIL` to that account's email.
3. Set `ADMIN_BOOTSTRAP_ENABLED=true`.
4. Deploy the backend once.
5. Confirm the deployment starts and the account can obtain a fresh token with role `ADMIN`.
6. Set `ADMIN_BOOTSTRAP_ENABLED=false` and clear `ADMIN_BOOTSTRAP_EMAIL`.
7. Deploy again so routine restarts no longer execute bootstrap logic.

Promotion is idempotent: an account already holding `ADMIN` remains unchanged. Existing access tokens retain their original claims until login or refresh issues a new token.

### Failure behavior

When bootstrap is enabled:

- a blank or invalid email causes startup to fail;
- a target account that does not exist causes startup to fail;
- logs describe the bootstrap result without printing the configured email, passwords, or tokens.

This fail-closed behavior prevents a deployment from appearing healthy when the requested administrator promotion did not happen.

## Immutable audit log

`admin_audit_logs` is the append-only source for successful administrator mutations. TON-194 provides the storage/read foundation; TON-195 is the first mutation flow that writes audit records transactionally.

Each record contains:

```text
id          UUID audit record ID
actorId     authenticated administrator user ID
action      controlled AdminAuditAction value
targetType  POST, USER, or RANKING
targetId    bounded string identifying the affected target
reason      required administrator reason
metadata    bounded flat JSON object
createdAt   UTC instant
```

Initial action vocabulary:

```text
ADMIN_HIDE_POST
ADMIN_RESTORE_POST
ADMIN_DELETE_POST
ADMIN_SUSPEND_USER
ADMIN_BAN_USER
ADMIN_REVOKE_SESSIONS
ADMIN_REBUILD_RANKING
```

### Append contract

Audit records are created only through the internal transactional `AdminAuditLogService.append(...)` API. There is no public HTTP endpoint that accepts arbitrary audit events.

The append service:

- requires actor, action, target type, target ID, and reason;
- trims target ID, reason, keys, and values;
- limits reason to 500 characters and target ID to 128 characters;
- limits metadata to 20 flat string entries;
- allows lowercase machine keys up to 50 characters;
- limits each metadata value to 200 characters and the serialized payload to 3 KiB;
- rejects metadata keys containing password, secret, token, authorization, cookie, credential, or email terms;
- rejects metadata values containing email-like `@` content, bearer-token text, or control characters;
- never includes rejected raw metadata values in validation exceptions.

Metadata is intended for non-sensitive operational context such as request IDs, previous status values, feed names, and bounded counts. Do not store email addresses, display names, request bodies, cookies, provider payloads, access tokens, refresh tokens, password material, or secrets.

### Append-only enforcement

The application model uses:

- a Hibernate `@Immutable` entity;
- a read-only Spring Data repository surface with no save/update/delete methods;
- a dedicated insert-only store based on `EntityManager.persist`.

PostgreSQL also installs a trigger that rejects every `UPDATE` or `DELETE` against `admin_audit_logs`. This protects against accidental application or SQL mutation. It is not a claim of cryptographic tamper evidence against a database owner who can alter schema, triggers, or backups.

### Read API

```http
GET /api/v1/admin/audit-logs?page=0&size=20
Authorization: Bearer <admin access token>
```

Optional exact-match filters:

```text
action
actorId
targetType
targetId
```

Example:

```http
GET /api/v1/admin/audit-logs?action=ADMIN_HIDE_POST&targetType=POST&targetId=aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa&page=0&size=20
Authorization: Bearer <admin access token>
```

The response uses an explicit stable page DTO:

```json
{
  "content": [
    {
      "id": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
      "actorId": "cccccccc-cccc-cccc-cccc-cccccccccccc",
      "action": "ADMIN_HIDE_POST",
      "targetType": "POST",
      "targetId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
      "reason": "Contains prohibited content",
      "metadata": {
        "previous_status": "VISIBLE",
        "new_status": "HIDDEN"
      },
      "createdAt": "2026-07-30T07:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

Ordering is always deterministic:

```text
createdAt DESC, id DESC
```

Page is zero-based. Size must be from 1 through 100.

### Retention and archival

TON-194 does not add automatic deletion, expiry, export, or archival. Audit records remain in PostgreSQL. A future retention policy must preserve append-only guarantees and use an explicitly reviewed migration or archival process rather than application delete methods.

## Ballot moderation

Ballot lifecycle and moderation are separate state machines:

```text
BallotStatus       OPEN | CLOSED
ModerationStatus   VISIBLE | HIDDEN | DELETED
```

Protected mutations:

```http
POST /api/v1/admin/posts/{postId}/hide
POST /api/v1/admin/posts/{postId}/restore
POST /api/v1/admin/posts/{postId}/delete
Authorization: Bearer <admin access token>
Content-Type: application/json

{"reason":"Violates published community rules"}
```

Transition rules:

- `VISIBLE -> HIDDEN` through hide;
- `HIDDEN -> VISIBLE` through restore;
- `VISIBLE/HIDDEN -> DELETED` through administrator soft-delete;
- `DELETED` is terminal in this phase;
- repeated or incompatible transitions return `409 Conflict` without another audit record.

Each transition acquires a pessimistic ballot lock. Moderation state and its matching `ADMIN_*_POST` audit row commit in one PostgreSQL transaction. Audit failure rolls back the state change. Ranking and SSE effects are transaction-bound and run only after commit.

Hidden and deleted ballots are excluded from public detail, every public feed, voting, SSE subscription, and author mutations. Those paths return a generic not-found response and do not reveal moderation status or reason. Administrator soft-delete preserves the ballot and vote rows; it is distinct from the existing author-owned hard delete.

Detailed state, visibility, Redis, SSE, and concurrency rules: [`MODERATION.md`](MODERATION.md).

## Verification

Backend tests cover:

- anonymous `401` and authenticated `USER` `403` at admin boundaries;
- issued `ADMIN` JWT access and direct method-security rejection;
- registration response remains `USER`;
- disabled, normalized, missing-target, and idempotent bootstrap behavior;
- audit validation, metadata privacy, JSONB persistence, filtering, and deterministic ordering;
- database-trigger rejection of audit updates and deletes;
- ballot hide, restore, and terminal soft-delete transitions;
- preservation of lifecycle state across moderation;
- public feed/detail/vote/SSE/owner-mutation visibility enforcement;
- stale Redis-member filtering and post-commit convergence;
- transaction rollback when audit validation fails;
- concurrent moderation producing one state transition and one audit record.
