# TON-110 comments foundation verification

## Delivered contract

- Guests can read comments for a publicly visible ballot.
- Authenticated users can create top-level comments or one-level replies.
- Authors alone can edit or remove their comments.
- Public pagination uses the stable ascending `(created_at, id)` cursor and joins the visible ballot in the same SQL statement, so a concurrent hide cannot expose comment bodies.
- Comment author summaries reuse the privacy-safe display name, initials, avatar icon, and avatar color contract.
- Removed/moderated rows return a tombstone with no previous body.
- Ballot responses expose a visible `commentCount` updated transactionally with comment creation/removal.
- COMMENT reports require an existing visible comment, reject self-reporting, and produce `VERIFIED` moderation targets.
- Comment create/edit routes use user-scoped Redis sliding-window rate limits.
- Flyway V18 keeps comments outside Supabase browser-role access and enables RLS.

## Concurrency invariants

Comment writes acquire locks in this order:

```text
post row -> parent/comment row
```

Creation increments `commentCount` while holding the ballot lock. Edit and removal first resolve the scalar post ID, lock the visible ballot, and then lock the comment. Concurrent creates therefore cannot lose count updates, and parent removal cannot race a new reply past the visible-parent check.

## Deferred boundary

TON-110 establishes comment moderation status and public redaction but does not add administrator comment transitions. TON-111 owns hide, restore, remove, audit integration, and comment voting. A verified comment moderation case cannot be resolved through an incompatible ballot or user action.

## Merge gate

Merge only after focused comment/moderation/rate-limit tests, full backend/frontend CI, OSV scan, production image build, production runtime smoke, and review-thread checks pass on the exact final head.
