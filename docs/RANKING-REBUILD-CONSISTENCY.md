# Ranking rebuild consistency

PostgreSQL is authoritative for ballot visibility, creation, vote aggregates, and ranking membership. Redis ranking generations are rebuildable derived state.

## Lost-update risk

An atomic Redis pointer swap alone does not make the staged generation current. Without a database fence, this sequence could publish stale data:

```text
rebuild stages ballot A with score 10
vote transaction commits score 11
vote event updates the old active Redis generation
rebuild publishes the staged generation with score 10
```

The same race applies to ballot create/delete and administrator hide/restore/delete.

## PostgreSQL revision fence

Flyway creates one `ranking_revision` singleton row. Every transaction that changes ranking score or membership increments this revision in the same PostgreSQL transaction as the authoritative mutation.

Covered mutations:

- ballot create and author delete;
- vote add, change, and remove when the aggregate changes;
- administrator hide, restore, and soft-delete.

No-op repeated vote/unvote requests do not increment the revision. Ballot title/content/category edits and lifecycle close do not currently change ranking score or membership and therefore do not increment it.

Revision updates and the rebuild publish boundary share a PostgreSQL advisory transaction lock named `vote-system:ranking-consistency`.

## Rebuild protocol

A rebuild keeps the Redis single-flight lock and performs at most three attempts:

1. Read the current PostgreSQL ranking revision.
2. Read publicly visible ballots and stage a temporary HOT/TOP_DAY/TOP_WEEK generation.
3. Verify staged member counts against the authoritative snapshot.
4. Start a short PostgreSQL transaction and acquire the consistency advisory lock.
5. Read the revision again.
6. When the revision changed, discard the temporary generation and retry from a fresh snapshot.
7. When stable, append the administrator audit row and atomically publish the Redis generation.
8. Commit the PostgreSQL transaction, release the consistency boundary, then release the Redis single-flight lock.

A mutation that begins before or during step 4 either commits first and changes the revision, forcing a retry, or waits at the advisory lock and commits afterward. Its after-commit ranking event then targets the newly active Redis generation.

## Failure behavior

- Repeated revision changes exhaust the three-attempt budget and return `409 Conflict`.
- Unstable attempts do not append successful rebuild audit rows.
- A publish or audit failure discards or rolls back the candidate and preserves the previous active generation.
- Public ranked feeds retain their PostgreSQL fallback when Redis is unavailable.
- Metrics distinguish requested, retried, succeeded, failed, and rejected rebuilds.

The revision fence addresses snapshot correctness. It does not make the rebuild bulk scan cheap. Paging PostgreSQL reads, Redis pipelining, and lock-watchdog scalability remain separate operational work.

## Verification

Integration coverage deterministically coordinates mutations after staging and before publish:

- a concurrent vote changes the score and the stable retry publishes the new score;
- a concurrent create appears in the published generation;
- concurrent moderation removes the hidden ballot from the published generation;
- repeated creates exhaust the retry budget without changing the active pointer or adding an audit row;
- existing authorization, distributed-lock rejection, audit rollback, status, and Redis fallback behavior remain covered.
