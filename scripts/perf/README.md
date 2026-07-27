# Production API latency benchmark

This script collects sequential warm-request samples for the Vote System production API and writes a JSON report plus a flat CSV file. It never writes access tokens, cookies, user IDs, or concrete post IDs to the report.

## Requirements

- Node.js 22+
- A production test account for authenticated scenarios
- A currently active ballot for the vote scenario

## Public feed

```bash
SCENARIOS=public-feed \
SAMPLES=50 \
WARMUP=5 \
RAILWAY_REGION=asia-southeast1 \
SUPABASE_REGION=ap-southeast-1 \
UPSTASH_REGION=ap-southeast-1 \
node scripts/perf/benchmark-api.mjs
```

## Authenticated feed

```bash
SCENARIOS=authenticated-feed \
ACCESS_TOKEN='<temporary access token>' \
SAMPLES=50 \
node scripts/perf/benchmark-api.mjs
```

## Vote cast/remove

The script first sends an unmeasured `DELETE` to normalize the test account to a no-vote state. It then alternates `PUT` and `DELETE`, so `SAMPLES` must be even. The default 2.1-second delay keeps 50 measured operations below the production vote rate limit.

```bash
SCENARIOS=vote \
ACCESS_TOKEN='<temporary access token>' \
POST_ID='<active ballot UUID>' \
VOTE_TYPE=UP \
SAMPLES=50 \
VOTE_DELAY_MS=2100 \
node scripts/perf/benchmark-api.mjs
```

## Refresh rotation

Pass only the cookie header copied from a test request. The script keeps rotated `Set-Cookie` values in memory and never writes them to disk. The default 3.1-second delay stays below the production refresh rate limit.

```bash
SCENARIOS=refresh \
COOKIE_HEADER='vote_refresh=<temporary refresh token>' \
SAMPLES=50 \
REFRESH_DELAY_MS=3100 \
node scripts/perf/benchmark-api.mjs
```

## Combined run

```bash
SCENARIOS=public-feed,authenticated-feed,vote,refresh \
ACCESS_TOKEN='<temporary access token>' \
COOKIE_HEADER='vote_refresh=<temporary refresh token>' \
POST_ID='<active ballot UUID>' \
SAMPLES=50 \
RAILWAY_REGION=asia-southeast1 \
SUPABASE_REGION=ap-southeast-1 \
UPSTASH_REGION=ap-southeast-1 \
node scripts/perf/benchmark-api.mjs
```

## Validate configuration without sending requests

```bash
DRY_RUN=1 \
SCENARIOS=vote \
ACCESS_TOKEN=dummy \
POST_ID=00000000-0000-0000-0000-000000000000 \
node scripts/perf/benchmark-api.mjs
```

## Output

Reports are written to `perf-results/` by default:

- `api-benchmark-<timestamp>.json`
- `api-benchmark-<timestamp>.csv`

Each scenario summary includes count, failures, min, mean, p50, p95, p99, and max. Individual rows contain only the route template and safe request ID; concrete ballot IDs are not persisted.

## Decision gate

- Vote warm p50 ≤ 500 ms and p95 ≤ 1 s: defer database refactoring.
- Vote misses the target: inspect `vote.operation.stage` and optimize only the dominant measured stage.
- Refresh misses while vote/feed pass: move auth optimization to its own task.
