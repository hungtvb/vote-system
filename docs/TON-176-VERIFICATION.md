# TON-176 verification

This note records the verification boundary for the public `READ_ONLY` and `MAINTENANCE` experience.

## Covered behavior

- public status polling is presentation-only and fails open when the status endpoint is unavailable;
- stable backend problem codes immediately reconcile stale public UI state;
- `READ_ONLY` preserves reads and login while disabling every exposed business-write entry point;
- `MAINTENANCE` replaces the public workspace, stops feed activity, and provides an authoritative retry path;
- administrator-authored localized messages are displayed without rewriting their content;
- backend request enforcement remains the security boundary in every mode.

## Required checks

- TypeScript system-mode and API lifecycle tests;
- Vietnamese and English catalog parity;
- Ballot Mark asset and contrast checks;
- production Next.js build;
- existing visual regression baseline;
- backend verification and production runtime smoke.

The pull request must not be merged until the repository CI gates complete successfully on its current head SHA.
