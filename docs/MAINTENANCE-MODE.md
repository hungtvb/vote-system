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

## Recovery procedure

1. Keep an existing administrator session or refresh cookie available.
2. Enable `MAINTENANCE` through `PUT /api/v1/admin/system/status`.
3. Confirm public feed requests return `SYSTEM_MAINTENANCE`.
4. After a browser refresh, restore the administrator session through `POST /api/v1/auth/refresh`.
5. Return the mode to `NORMAL` through the administrator status endpoint.
6. Confirm public feed and supported writes are available again.

The automated integration kill-test covers this full sequence. TON-177 will repeat it against the deployed topology and attach production evidence.

## Follow-up boundaries

- TON-175 adds the protected System Operations control to the table-first admin dashboard.
- TON-176 renders the public read-only banner and maintenance screen.
- TON-177 verifies deployed recovery and administrator lockout scenarios.
