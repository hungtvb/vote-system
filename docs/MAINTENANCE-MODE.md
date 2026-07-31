# System operating mode

Vote System stores one authoritative operating-mode record in PostgreSQL. TON-173 provides persistence and status APIs; request enforcement and user-facing maintenance behavior are separate follow-up tickets.

## Modes

```text
NORMAL       normal supported behavior
READ_ONLY    intended for public reads while writes are blocked by TON-174
MAINTENANCE  intended for outage/recovery routing enforced by TON-174
```

TON-173 alone does not block application requests. Until TON-174 is deployed, changing the stored mode only changes the status contracts and audit record.

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

Anonymous in every stored mode. The response excludes the administrator actor:

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

Both require an active `ADMIN` token. An update body contains:

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

The dedicated public-message metadata fields may contain public contact addresses. Other audit metadata fields retain the stricter email/token privacy checks from the audit foundation.

The audit append and status mutation share one PostgreSQL transaction. Validation or audit failure rolls both back.

## Follow-up boundaries

- TON-174 enforces READ_ONLY and MAINTENANCE at the backend request boundary.
- TON-176 renders the public read-only banner and maintenance screen.
- TON-175 adds the protected System Operations control to the table-first admin dashboard.
- TON-177 verifies production recovery and administrator lockout scenarios.
