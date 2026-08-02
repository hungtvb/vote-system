# TON-140 — Unified reports and moderation-case workflow

## Delivered boundary

- Authenticated users can submit immutable reports for ballot, user, and deferred comment targets.
- Reporter history is private and cursor-ordered by `(created_at, id)`.
- Administrators can list, assign, triage, review, resolve, reject, and reopen moderation cases.
- Ballot and user resolutions reuse the existing audited moderation services.
- Comment targets remain `DEFERRED` until the comment domain exists; they cannot be resolved as though they were verified.
- The new tables revoke Supabase browser roles and enable row-level security.

## Concurrency and atomicity

- PostgreSQL transaction advisory locks serialize active-case creation per target.
- Partial unique indexes prevent duplicate active reports and duplicate active cases under races.
- Pessimistic case locks serialize administrator transitions.
- Timed user resolutions are normalized to PostgreSQL microsecond precision before comparison and persistence.
- Target adapters may clear the JPA persistence context during bulk session revocation. The service therefore reacquires the case lock after the target action and before changing case/report state.
- Target action, case transition, report closure, and audit records remain in one transaction; failures roll back the full operation.

## Focused verification

The publication gate compiled the backend and passed:

- `ModerationCaseIntegrationTests`
- `RedisSlidingWindowRateLimiterSecurityTests`
- the dedicated user-suspension regression that verifies:
  - the user is suspended,
  - the case and reports are persisted as resolved,
  - both target and case audits are written once,
  - replaying the same resolution remains idempotent.

Full repository CI, dependency scanning, code review, production image build, and runtime smoke are required on the final PR head before merge.
