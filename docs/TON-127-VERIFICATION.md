# TON-127 verification

Evidence is valid only for the exact pull-request head and workflow runs recorded with it.

## Security boundary

The browser snapshot is presentation-only and versioned. It may contain the user ID, display name, initials, avatar preset/color, role label, and linked-provider icon identifiers. It must never contain an email address, biography, locale, timestamps, cookie, access token, refresh token, password, or authoritative authorization state.

A cached role label never unlocks the voter menu, administrator route, owner controls, provider mutations, or authenticated requests. Session restoration and the backend profile remain authoritative.

## Lifecycle acceptance

- A valid snapshot renders a noninteractive identity while refresh is pending.
- A successful refresh atomically replaces stale presentation data with the authoritative profile.
- A failed refresh, logout, logout-all, schema mismatch, version mismatch, user mismatch, malformed JSON, or browser-storage error clears or ignores the snapshot without breaking authentication.
- Reduced-motion users receive no snapshot spinner animation.
- Compact masthead rendering has no horizontal overflow.
- QA response cookies use fixed literal values selected from explicit fixture branches; query input is never interpolated into a response header.

## Merge gate

The exact pull-request head must pass frontend model tests, Next.js production build, Auth Bootstrap browser QA, Locale and Admin visual QA, dependency scanning, full Maven verification, production image build, and production-profile runtime smoke before merge.
