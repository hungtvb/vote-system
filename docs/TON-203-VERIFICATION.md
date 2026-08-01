# TON-203 verification

Evidence is valid only for the exact commit and workflow run recorded with it.

## Automated acceptance

- PostgreSQL rows are read through a lightweight projection in bounded pages.
- Redis staging writes use multi-member pipelined ZADD commands per database batch.
- Each generated HOT, TOP_DAY, and TOP_WEEK key receives one staging expiry.
- The rebuild lease is renewed only by its token owner; stale tokens cannot renew, publish, roll back, or release it.
- Closing the watchdog suppresses post-close renewal results so shutdown cannot create a false failure metric.
- Revision changes discard staging and consume the existing bounded retry budget.
- Redis staging failure preserves the previously published generation.
- Metrics and logs use bounded outcome values and contain no user, ballot, cookie, email, or token identifiers.

## Merge gate

The exact pull-request head must pass full Maven verification, frontend production build and visual QA, dependency scanning, production image build, and production-profile runtime smoke before merge.
