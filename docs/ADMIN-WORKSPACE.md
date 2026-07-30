# Protected administrator workspace

TON-199 introduces a static-export-compatible administrator interface at:

```text
/admin/
```

The page is a client component wrapped in `Suspense`, so `useSearchParams` remains compatible with the existing Next.js static export. It does not use server-only session state or dynamic rendering.

## Access behavior

The frontend guard is a UX boundary; Spring Security remains authoritative.

```text
guest                 restore fails, then return to the public sign-in flow
authenticated USER    render access denied and send no admin API request
active ADMIN           load protected administrator data
restricted ADMIN       backend account enforcement rejects refresh/JWT access
```

An ADMIN-only entry appears in the Voter ID menu. Manually entering `/admin/` does not bypass backend authorization.

The access token remains in React memory and every administrator request uses the existing single-flight refresh/retry lifecycle. The workspace does not create a second session store.

## Sections

Navigation state is URL-backed through `section`, `page`, `q`, and `state` query parameters. Browser refresh and copied links preserve the selected read view.

### Overview

Overview performs small protected page requests and displays authoritative totals for:

- registered users;
- registered ballots;
- immutable audit events.

Unavailable data is rendered as unknown, never as a fabricated zero. Ranking health is intentionally unavailable until TON-198 supplies a protected status contract.

### Users

The Users view consumes `GET /api/v1/admin/users` and shows operational email, role, effective account status, optional expiry, provider names, and safe timestamps.

Supported actions:

```text
ACTIVE      suspend, ban, revoke refresh sessions
SUSPENDED   restore
BANNED      restore
```

Self-moderation buttons are disabled in the UI, while the backend independently rejects self-lockout and last-active-administrator violations.

### Ballots

The Ballots view consumes `GET /api/v1/admin/posts` and includes visible, hidden, and soft-deleted records. Lifecycle and moderation remain distinct.

Supported actions:

```text
VISIBLE   hide or soft-delete
HIDDEN    restore or soft-delete
DELETED   read-only terminal record
```

### Audit

The Audit view consumes `GET /api/v1/admin/audit-logs`. It is read-only and presents action, actor, target, reason, bounded metadata, and timestamp.

### Ranking

The Ranking section renders an explicit dependency-pending state. It does not infer Redis health, membership counts, last rebuild time, or a zero value. TON-198 owns the status and rebuild contract.

## Mutation dialog

Every user or ballot mutation opens a native modal dialog with:

- descriptive target context;
- a required reason limited to 500 characters;
- optional future expiry for suspend/ban;
- cancel and close controls;
- busy and safe error states.

The reason warning reminds administrators not to enter email, tokens, or sensitive data. Successful mutation responses are not treated as the final screen state; the current section is reloaded from the backend after commit.

## Responsive behavior

The workspace follows the Ballot Edition visual system:

- Navy operational header;
- Kraft/Bone registry surface;
- Seal red destructive emphasis;
- mono labels and counts;
- serif record headings;
- strong rectangular borders rather than generic dashboard cards.

Desktop uses a persistent sidebar. Tablet and mobile use a horizontally scrollable section strip while preventing page-level horizontal overflow. Record rows collapse into stacked cards, actions remain accessible, and modal controls become single-column on compact screens.

## Internationalization

All product-owned workspace copy is present in Vietnamese and English under the `admin` catalog. Backend enum/action values remain language-neutral. Email, ballot titles, reasons, and other user-generated or operational values are never auto-translated.

## Verification

Automated coverage includes:

- typed administrator API query and mutation payload tests;
- frontend test/build and catalog parity;
- guest redirect to the public authentication flow without admin API calls;
- authenticated USER denial without admin API calls;
- ADMIN overview at 390, 768, and 1440 pixel widths;
- mobile reason-dialog focus and required reason field;
- audit rendering and ranking unknown-state rendering;
- page-level horizontal-overflow assertions;
- existing auth bootstrap and VI/EN visual gates;
- production image and runtime smoke through the main CI pipeline.

Browser evidence is uploaded by the `Admin Workspace QA` workflow.
