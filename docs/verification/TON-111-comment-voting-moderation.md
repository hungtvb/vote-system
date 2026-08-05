# TON-111 comment voting and moderation verification

## Delivered contract

- Authenticated users can add, change, or remove one UP/DOWN vote per visible comment.
- Comment responses expose `voteScore`, `upVotes`, `downVotes`, and authenticated `myVote`.
- Database and entity invariants require non-negative counts and `voteScore = upVotes - downVotes`.
- Votes lock the visible parent ballot for read and the comment for write; comment/admin mutations use parent ballot write then comment write.
- Administrators can hide, restore, or terminally remove comments through audited COMMENT actions.
- Public non-visible comments remain tombstones with no prior body.
- Visibility transitions update parent ballot `commentCount` in the same transaction.
- COMMENT moderation cases resolve through the same admin comment service using `HIDE_COMMENT`, `RESTORE_COMMENT`, or `DELETE_COMMENT`.
- Comment-vote requests use an independent user-scoped Redis sliding-window limit.
- Flyway V19 keeps comment votes backend-owned with RLS and revoked Supabase browser-role privileges.

## Atomicity and privacy invariants

```text
post read  -> comment write -> vote row/aggregate   (comment voting)
post write -> comment write -> count/audit          (owner/admin moderation)
```

A failed audit append rolls back comment state and parent count. Hidden/deleted comments reject vote mutations and reports. Vote rows remain stored across moderation, while public response bodies remain redacted for every non-visible state.

## Merge gate

Merge only after focused vote, concurrency, moderation, moderation-case, rate-limit, migration, and system-mode tests pass, followed by full backend/frontend CI, OSV, production image/runtime smoke, and review-thread checks on the exact final head.
