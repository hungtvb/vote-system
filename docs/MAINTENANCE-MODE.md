# System operating mode

Vote System stores one authoritative operating-mode record in PostgreSQL and enforces it at the Spring Security request boundary.

## Modes

| Mode | Public reads | Login | Registration | Business writes |
|---|---:|---:|---:|---:|
| `NORMAL` | Allow | Allow | Allow | Allow |
| `READ_ONLY` | Allow | Allow | Block | Block |
| `MAINTENANCE` | Block | Block | Block | Block |

Business writes include voting and ballot creation, editing, closing, deletion, plus other non-recovery mutations. The frontend behavior in TON-176 improves the experience, but the backend filter remains the security boundary.

## Persistence

Flyway V13 creates the singleton `system_status` row with:

- mode;
- Vietnamese and English administrator-authored messages;
- optional estimated end time;
- update timestamp;
- administrator actor ID;
- optimistic version.

PostgreSQL is the source of truth. Redis does not store the operating mode.

Administrator mutations use a pessimistic row lock so concurrent updates are serialized. `NORMAL` clears messages and the estimated end time to prevent stale maintenance copy from remaining active.

## APIs

### Public status

```http
GET /api/v1/system/status
```

Anonymous and reachable in every mode. The response excludes the administrator actor:

```json
{
  "mode": "MAINTENANCE",
  "messageVi": "Hệ thống đang bảo trì",
  "messageEn": "The system is under maintenance",
  "estimatedEndAt": "2026-07-31T04:00:00Z",
  "updatedAt": "2026-07-31T02:00:00Z"
}
```

### Administrator status

```http
GET /api/v1/admin/system/status
PUT /api/v1/admin/system/status
```

Both require an active `ADMIN` token. They remain reachable in every mode so an administrator can inspect and restore the system.

An update body contains:

```json
{
  "mode": "READ_ONLY",
  "messageVi": "Hệ thống tạm thời chỉ cho phép xem",
  "messageEn": "The system is temporarily read-only",
  "estimatedEndAt": "2026-07-31T04:00:00Z",
  "reason": "Database maintenance"
}
```

Messages are optional and limited to 200 characters per locale. The reason is required and limited to 500 characters. A non-null estimated end time must be in the future. Repeating the same effective state returns `409 Conflict` and does not add a duplicate audit event.

## Request enforcement

`SystemModeEnforcementFilter` runs after CORS and before authenticated application processing.

The following recovery routes remain reachable according to their existing authorization rules:

```text
OPTIONS *
GET|HEAD /actuator/health[/...]
GET|HEAD /api/v1/system/status
GET|HEAD|PUT /api/v1/admin/system/status
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
POST /api/v1/auth/logout-all
```

An exemption bypasses only the operating-mode filter. It does not bypass Spring Security; a normal user still receives `403` when calling the administrator status endpoint.

`READ_ONLY` additionally allows safe `GET`/`HEAD` requests and supported local/social login flows. It does not allow registration or business mutations.

`MAINTENANCE` rejects public application and login traffic while preserving the recovery routes above. There is no bypass query parameter, secret URL, or hard-coded recovery credential.

### Rejection contract

Blocked requests return `503 Service Unavailable` with `application/problem+json`.

```json
{
  "type": "about:blank",
  "title": "System is read-only",
  "status": 503,
  "detail": "This operation is unavailable while the system is read-only",
  "instance": "/api/v1/posts",
  "timestamp": "2026-07-31T02:00:00Z",
  "code": "SYSTEM_READ_ONLY",
  "mode": "READ_ONLY"
}
```

Stable codes:

- `SYSTEM_READ_ONLY` — the current mode blocks a mutation;
- `SYSTEM_MAINTENANCE` — maintenance blocks the application request;
- `SYSTEM_STATUS_UNAVAILABLE` — the backend cannot verify the authoritative mode.

When a future estimated end time exists, the response includes `Retry-After` in whole seconds. CORS exposes this header to the frontend.

If the status lookup fails, non-recovery traffic fails closed with `SYSTEM_STATUS_UNAVAILABLE`; unconditional recovery routes remain reachable without reading the status row. This avoids silently enabling writes while preserving an administrator recovery path.


## Public frontend behavior

The public Next.js application loads `GET /api/v1/system/status` during startup, refreshes it every 30 seconds, and reloads it when the page regains focus. The status request is deliberately fail-open for presentation: a failed request keeps the current UI state and never invents `MAINTENANCE`. Backend request enforcement remains authoritative.

The shared API transport preserves the stable problem `code` and `mode` fields. A `SYSTEM_READ_ONLY` or `SYSTEM_MAINTENANCE` response from any application request immediately reconciles the public UI, even when the most recent status poll is stale. The app then reloads the public status endpoint to obtain the administrator-authored message and estimated end time.

### `READ_ONLY`

- public feed, search, filters, ballot details, public profiles, login and logout remain available;
- a persistent bilingual Ballot Edition banner presents the localized administrator message without rewriting it;
- voting, registration, ballot creation, editing, closing and deletion are disabled;
- profile editing and social-provider linking are disabled as additional business mutations;
- any open write dialog is closed when the mode changes.

### `MAINTENANCE`

- the public application is replaced by a full-page bilingual service notice;
- feed, session and mutation error noise is hidden behind the notice;
- active feed requests and vote-stream subscriptions are stopped;
- the administrator message and estimated restoration time are shown when supplied;
- the retry action reloads the authoritative public status;
- polling or retrying a `NORMAL` response restores the public feed without a page reload.

The notice and banner use status semantics, keyboard-operable controls, preserved line breaks for administrator-authored messages, mobile-safe wrapping, and reduced-motion handling.

## Local cache

Reads use a process-local five-second cache:

- a cache miss loads the singleton row from PostgreSQL;
- an administrator mutation evicts the cache before acquiring the row lock;
- the changed snapshot enters the cache only in transaction `afterCommit`;
- rollback cannot expose uncommitted state;
- explicit eviction forces the next read to reload PostgreSQL, matching restart behavior.

This is intentionally single-instance behavior. Redis Pub/Sub or another cross-instance invalidation mechanism is deferred until horizontal scaling.

## Audit

Every successful state change appends `SYSTEM_MODE_CHANGED` targeting `SYSTEM/GLOBAL` in the immutable administrator audit log.

Metadata contains:

- previous and new mode;
- full bounded Vietnamese and English public messages;
- estimated end time;
- bounded request ID.

The dedicated public-message metadata fields may contain public contact addresses. Other audit metadata fields retain the stricter email, cookie and token privacy checks from the audit foundation.

The audit append and status mutation share one PostgreSQL transaction. Validation or audit failure rolls both back.

## Operating procedure

### Before enabling a restricted mode

1. Confirm at least one active `ADMIN` account can open the protected System Operations control.
2. Keep that administrator's refresh cookie available in the same browser. Do not copy the cookie into tickets, chat, screenshots, shell history, or logs.
3. Record an incident/change identifier and the planned restoration time.
4. Confirm PostgreSQL is reachable. Redis is derived infrastructure and is not the authority for system mode.
5. Verify these recovery routes before changing mode:
   - `GET /actuator/health`;
   - `GET /api/v1/system/status`;
   - `POST /api/v1/auth/refresh` with the active administrator browser session;
   - `GET /api/v1/admin/system/status` with the administrator access token.

### Enable and verify `READ_ONLY`

1. Select `READ_ONLY` in the admin dashboard and provide bounded VI/EN public copy, an optional future estimated end, and a required reason.
2. Confirm `GET /api/v1/system/status` returns `READ_ONLY`.
3. Confirm feed/detail reads still succeed.
4. Confirm vote, registration, create, edit, close, and delete requests return `503 SYSTEM_READ_ONLY`.
5. Confirm the public banner appears at 1440, 768, 390, 375, and 320 px without horizontal overflow.

### Enable and recover from `MAINTENANCE`

1. Select `MAINTENANCE` in the admin dashboard and provide the operating notice, optional future estimated end, and incident reason.
2. Confirm public feed/detail and writes return `503 SYSTEM_MAINTENANCE`; health and public status must remain reachable.
3. Hard refresh the administrator browser.
4. Restore the administrator session through `POST /api/v1/auth/refresh` using the existing secure refresh cookie.
5. Open System Operations and confirm the authoritative mode is still `MAINTENANCE`.
6. Return mode to `NORMAL` through `PUT /api/v1/admin/system/status`.
7. Confirm public status is `NORMAL`, feed/detail reads recover, and a real business write such as a vote succeeds.
8. Confirm the enable and disable audit records contain the bounded request IDs used for the change.

The CI production-profile kill-test repeats this sequence across a backend restart, then separately removes Redis. It proves that PostgreSQL retains the mode, the administrator refresh path survives the backend restart while Redis is healthy, security-critical refresh fails closed during the Redis outage, an already-restored administrator token can still use the unconditional recovery endpoint, and normal reads/writes recover after Redis returns. It does not replace a controlled post-deployment smoke against the current Railway/Vercel/Supabase release.

## Emergency PostgreSQL recovery

Use direct database recovery only when all application-level administrator recovery paths are unavailable and the incident owner has approved bypassing the normal audit path. This is a last-resort Supabase/PostgreSQL operation, not a routine mode change.

Before changing data, capture the current row and preserve it in the incident record:

```sql
BEGIN;

SELECT singleton_id,
       mode,
       message_vi,
       message_en,
       estimated_end_at,
       updated_at,
       updated_by,
       version
FROM system_status
WHERE singleton_id = 1
FOR UPDATE;

UPDATE system_status
SET mode = 'NORMAL',
    message_vi = NULL,
    message_en = NULL,
    estimated_end_at = NULL,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = NULL,
    version = version + 1
WHERE singleton_id = 1;

COMMIT;
```

After emergency recovery:

1. Verify exactly one singleton row exists and it reports `NORMAL`.
2. Restart the Railway backend or wait longer than the five-second process-local cache, then verify `GET /api/v1/system/status` returns `NORMAL`.
3. Confirm feed and one controlled business write recover.
4. Record who approved and executed the SQL, the prior row values, timestamps, and the reason in the incident system. Direct SQL cannot append the application `SYSTEM_MODE_CHANGED` audit event.
5. Investigate and repair the failed administrator recovery path before the next maintenance window.

Never delete the singleton row, change `singleton_id`, weaken the database constraints, or store a recovery secret in the repository.

## Observability and evidence

Rejected mode-boundary requests increment `vote.system.mode.requests` with bounded tags only:

```text
result=rejected
status=503
code=SYSTEM_READ_ONLY|SYSTEM_MAINTENANCE|SYSTEM_STATUS_UNAVAILABLE|UNKNOWN
mode=NORMAL|READ_ONLY|MAINTENANCE|UNKNOWN
method=GET|HEAD|POST|PUT|PATCH|DELETE|OPTIONS|OTHER
route=system-status|admin-system-status|auth|posts|users|oauth|actuator|other
```

The metric intentionally excludes raw paths, user IDs, ballot IDs, emails, cookies, request IDs, and tokens. Request correlation remains in `http_request_complete requestId=<bounded-id>` logs and in the system-mode audit metadata.

Attach evidence for the exact release under test:

- backend and frontend commit SHA;
- CI workflow run and production-profile runtime-smoke artifact;
- safe status codes/problem codes for NORMAL, READ_ONLY, and MAINTENANCE;
- responsive frontend screenshots/HTML evidence;
- audit records with bounded request IDs;
- metric output containing only bounded tag values;
- Railway/Vercel deployment identifiers and test timestamp;
- confirmation that evidence and logs contain no cookie, JWT, email, or bootstrap secret.

## Follow-up boundaries

- TON-175 added the protected System Operations control to the table-first admin dashboard.
- TON-176 implemented the public read-only banner, maintenance screen, global problem reconciliation and write-entry-point controls.
- TON-177 adds restart/Redis-loss recovery QA, bounded rejection metrics, operating guidance, and deployed-topology evidence.
