# TON-141 session and device security verification

## Delivered boundary

- Each refresh-session device owns a stable, non-secret family UUID that survives token rotation.
- Authenticated users can list only their own active session families, revoke one other family, or revoke all other families.
- The current family is marked from the access-token `session_family_id` claim; the claim is presentation/context only and is not an authorization source.
- Session responses expose only family ID, created/last-used/expiry timestamps, provider, coarse client label, and current status.
- Refresh tokens, token hashes, raw fingerprints, IP addresses, and raw user-agent values are never returned or persisted in account-security events.
- Sign-in, session revocation, and suspicious refresh-token reuse create bounded account-security events. Email-verification and account-recovery event hooks exist without recovery-secret storage.
- Flyway V17 keeps `account_security_events` outside the Supabase browser Data API by revoking browser roles and enabling RLS.

## Concurrency invariants

All refresh-session mutations use the same lock order:

```text
user row -> refresh-session rows
```

The initial token-owner lookup is scalar only. The session entity is loaded with `FOR UPDATE` after the user lock, preventing a stale first-level-cache entity from surviving a concurrent family revocation.

Refresh rotation is written in three ordered phases:

1. revoke and flush the current active row;
2. insert and flush the replacement row;
3. link the old row to the replacement and flush.

This preserves the partial unique index requiring one active row per family and the immediate self-referencing rotation-chain foreign key. A concurrent revoke cannot leave a replacement session active, and replaying a rotated token still revokes the active chain and records suspicious reuse.

## Pre-publication evidence

- Exact base: `0c8a2d154c2069140a996d38877e25dee9cd489a`.
- Original decoded implementation patch SHA-256: `e6c7205efb54dde5ada20d95bee54929a81693fdf54bbb3e3dfe5bb1afa0c074`.
- Race/rotation repair patch SHA-256: `f0e59ff08f03696202504fc513fd1299f8d20bc5b94ce6f6ca7d4f3b40edc2d4`.
- Backend compilation passed before branch publication.
- Focused suites passed for session metadata, session-management APIs, logout, refresh rotation/reuse, concurrent user restriction, administrator moderation, and Redis rate limiting.
- Strict publisher allowlist covered 15 modified and 14 new source files.
- Documentation was corrected to match the authoritative `user -> refresh sessions` lock order and the implemented private session APIs.

## Final merge gate

Merge only after CI backend/frontend, OSV dependency scan, production image build, production runtime smoke, and pull-request review-thread checks all pass on the exact final head.
