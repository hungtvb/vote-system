# Ballot moderation

This document describes the implemented administrator ballot-moderation contract and its boundary with the unified report/case workflow. User-account enforcement is documented separately.

## Separate state machines

Ballot lifecycle and moderation are independent:

```text
BallotStatus       OPEN | CLOSED
ModerationStatus   VISIBLE | HIDDEN | DELETED
```

`OPEN/CLOSED` controls editing, voting, close time, and verdict finalization. `VISIBLE/HIDDEN/DELETED` controls whether the ballot is available through public APIs.

Examples:

```text
OPEN + VISIBLE     public and voteable until closesAt
CLOSED + VISIBLE   public final result, not voteable
OPEN + HIDDEN      unavailable publicly, lifecycle preserved
CLOSED + HIDDEN    unavailable publicly, final result preserved
OPEN + DELETED     terminal moderation state, rows retained
CLOSED + DELETED   terminal moderation state, rows retained
```

Restoring a hidden ballot changes only moderation state. It does not reopen a closed ballot.

## Storage

Flyway V9 adds:

```text
posts.moderation_status      VISIBLE by default
posts.moderation_updated_at  timestamp of the latest moderation transition
```

Existing rows are backfilled through the `VISIBLE` default. The database constrains moderation values and requires a moderation timestamp for `HIDDEN` and `DELETED` rows.

Administrator deletion is a soft-delete. The ballot and its votes remain in PostgreSQL for auditability. The existing author-owned `DELETE /api/v1/posts/{postId}` flow remains a separate hard-delete behavior.

## Administrator API

All moderation routes require `ROLE_ADMIN` in both the request security chain and controller method guard.

```http
POST /api/v1/admin/posts/{postId}/hide
POST /api/v1/admin/posts/{postId}/restore
POST /api/v1/admin/posts/{postId}/delete
Authorization: Bearer <admin access token>
Content-Type: application/json
```

Request:

```json
{
  "reason": "Violates published community rules"
}
```

`reason` is required, trimmed by the audit service, and limited to 500 characters.

Response:

```json
{
  "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "moderationStatus": "HIDDEN",
  "moderationUpdatedAt": "2026-07-30T09:00:00Z"
}
```

## Transition rules

| Current state | Hide | Restore | Delete |
|---|---|---|---|
| `VISIBLE` | `HIDDEN` | conflict | `DELETED` |
| `HIDDEN` | conflict | `VISIBLE` | `DELETED` |
| `DELETED` | conflict | conflict | conflict |

Invalid or repeated transitions return `409 Conflict` and do not append another successful-action audit record.

## Atomic audit contract

Each mutation acquires a pessimistic ballot lock and performs the state transition plus audit append in one PostgreSQL transaction.

Successful actions append exactly one immutable record:

```text
hide       ADMIN_HIDE_POST
restore    ADMIN_RESTORE_POST
delete     ADMIN_DELETE_POST
TargetType POST
TargetId   ballot UUID
```

Safe metadata records only the previous and new moderation states. Audit validation or database failure rolls back the ballot transition. Redis ranking and SSE work is not executed before commit.

## Public visibility enforcement

Only `VISIBLE` ballots are returned through:

```http
GET /api/v1/posts
GET /api/v1/posts/{postId}
GET /api/v1/posts/{postId}/events
```

The same visibility rule applies to `LATEST`, `MINE`, `HOT`, `TOP_DAY`, and `TOP_WEEK` feeds.

For `HIDDEN` or `DELETED` ballots, public detail, vote, SSE, and author mutation paths return the same generic not-found response used for an absent ballot. They do not reveal moderation status or reason.

Voting is guarded twice:

- the pessimistic visible-ballot lookup rejects moderated rows;
- the aggregate update query also requires `moderation_status = 'VISIBLE'`.

Author update, close, and hard-delete operations use the same ballot lock as administrator moderation so concurrent transitions cannot bypass visibility enforcement.

## Redis ranking convergence

PostgreSQL remains authoritative. Ranked-feed reads re-check each Redis member against the `VISIBLE` PostgreSQL predicate before returning it. A stale Redis member therefore cannot expose a hidden or deleted ballot.

After a successful moderation commit:

- hide/delete publishes ranking removal;
- restore publishes ranking upsert from current PostgreSQL aggregates;
- the existing bounded ranking executor performs Redis I/O outside the request transaction.

Ranking rebuilds also load only visible ballots. Atomic generation-based manual rebuild remains owned by TON-198.

## SSE behavior

A new subscription is allowed only for a visible, active ballot. After hide or soft-delete commits, existing subscribers are completed through the bounded SSE executor. Restore does not reopen old connections; clients may subscribe again through the normal detail flow.

## Authorization and privacy

```text
anonymous request       -> 401 Unauthorized
authenticated USER      -> 403 Forbidden
authenticated ADMIN     -> transition rules apply
```

Public responses never include moderation state, moderation reason, audit metadata, or administrator identity. Audit metadata must not contain email, token, cookie, provider payload, IP address, raw user-agent, or request body values.

## Verification

The backend regression suite covers:

- request- and method-level authorization;
- hide, restore, and terminal soft-delete transitions;
- preservation of OPEN/CLOSED lifecycle across restore;
- public feed, detail, vote, SSE, and author-mutation enforcement;
- stale Redis-member filtering and post-commit convergence;
- preservation of ballot and vote rows after administrator soft-delete;
- audit/state rollback when audit validation fails;
- concurrent moderation producing one transition and one audit record.


## Comment-report boundary

TON-110 introduces the comment domain and changes new `COMMENT` reports from deferred validation to authoritative validation:

- the comment must exist;
- the comment and its ballot must both be publicly visible;
- an author cannot report their own comment;
- removed author tombstones and future hidden/deleted comments are not reportable.

A valid comment report creates or joins the same unified moderation-case workflow used by ballots and users with `targetValidationStatus=VERIFIED`. TON-110 intentionally does not add administrator comment actions. Hide, restore, remove, audit integration, and comment-vote moderation remain the scope of TON-111. Until then, a comment case may be triaged or rejected but cannot be resolved through a mismatched ballot/user action.
