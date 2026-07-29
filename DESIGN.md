# Vote System UI Design Specification

**Theme:** Ballot Edition  
**Scope:** Current public web product  
**Implementation:** Next.js App Router, TypeScript, SCSS Modules  
**Reference:** `design/stitch/ballot-edition/`

## 1. Source of truth

The approved Google Stitch export is stored under:

```text
design/stitch/ballot-edition/
├── README.md
├── stitch-design.md
├── feed.html
├── post-detail.html
├── create-post.html
└── auth.html
```

The export is visual reference material, not production code. Rebuild approved ideas as semantic Next.js components and SCSS Modules.

Priority when sources differ:

1. Current backend/API capability and privacy/security contract.
2. Current production component behavior.
3. This `DESIGN.md`.
4. Approved Stitch visual reference.
5. Generated HTML details.

## 2. Current product scope

Implemented:

- Email/password registration and login.
- Optional Google and GitHub social login and explicit account linking.
- Vietnamese/English system UI and language switcher.
- Editable display name, optional bio, preferred-locale value, and Ballot Mark identity.
- Ten custom Ballot Mark SVG presets and six approved colors.
- Private Voter ID and public-safe profile dialogs.
- Browse, search, filter, paginate, and open ballot details.
- LATEST, HOT, TOP DAY, TOP WEEK, and authenticated MY BALLOTS feeds.
- Create, edit, close, and delete owned ballots.
- Category, closing time, verdict threshold, and OPEN/CLOSED lifecycle.
- Upvote, downvote, change vote, repeated-choice removal, and explicit removal.
- Score, counts, threshold, projected/final verdict, and optimistic reconciliation.
- Realtime aggregate convergence through SSE.
- Logout and logout-all.
- Loading, empty, validation, auth, rate-limit, degraded-network, rollback, and success states.

Not implemented in the current public product:

- Free-form avatar/photo upload.
- Comments and replies.
- Notification inbox.
- Chat/direct messaging.
- Public bookmarks.
- File attachments or ballot images.
- Admin and moderation screens.

These are roadmap items, not inert UI placeholders. Do not expose controls before backend contracts exist.

## 3. Visual direction

Ballot Edition combines printed ballots, editorial layouts, filing cards, stamps, perforated rules, serial numbers, analog counters, and civic identity marks.

The product should feel trustworthy, deliberate, editorial, modern, lightweight, and slightly tactile. It must not resemble a generic SaaS dashboard, Material UI template, Bootstrap page, crypto product, glassmorphic interface, or AI-generated landing page.

### Core palette

| Token | Value | Usage |
|---|---:|---|
| Navy | `#1E2A3A` | primary actions, structure, UP state |
| Seal Red | `#B8342E` | DOWN state, destructive action, errors |
| Kraft | `#C9A876` | tabs, separators, secondary surfaces |
| Bone | `#F0E9D8` | page and paper background |
| Graphite | `#3A362E` | body text and neutral borders |
| Moss | approved token | optional Ballot Mark color |
| Ink Blue | approved token | optional Ballot Mark color |

### Typography and surfaces

- Headings: editorial serif such as Playfair Display.
- Body and controls: readable humanist sans such as Public Sans.
- Scores and metadata: monospace such as JetBrains Mono.
- Use sharp or nearly sharp corners, structural borders, restrained paper texture, and no soft glass shadows.

## 4. Current screens

### Masthead and feed

The masthead contains:

- Ballot Edition logo and identity;
- search;
- create-ballot action;
- compact VI/EN switcher;
- guest login/register state or authenticated Voter ID state.

The feed contains:

- provider actions only when backend discovery enables them;
- server-owned feed modes and filters;
- query, category, and OPEN/CLOSED controls;
- compact ballot cards with author Ballot Mark, timestamp, serial number, category, title, excerpt, counts, verdict, choices, and owner actions;
- loading, empty, error, result-count, page, and load-more states.

Public LATEST/HOT/TOP content begins loading before session restore completes. MY BALLOTS waits for authenticated identity. The UI must not blank the public feed while auth restore runs.

### Ballot detail

Show full content, public-safe author identity, lifecycle state, timestamps, category, closing time, vote totals, current user's REST-owned vote, threshold, verdict, and owner actions.

An open active ballot subscribes to one SSE stream. Shared aggregate updates may change counts and verdict; personal `myVote` remains owned by authenticated REST state.

Do not add comments, related content, or analytics until those contracts are implemented.

### Create and edit ballot

Use official-submission language consistent with Ballot Edition.

Supported fields:

- title;
- full content;
- category;
- closing time;
- verdict threshold.

Required behavior:

- client and server validation;
- disabled/submitting state;
- cancel and submit actions;
- create/update flow;
- delete confirmation through the Ballot Edition dialog;
- close confirmation for active ballots;
- unsaved-change protection where practical;
- clear distinction between `REGISTER`, `CREATE BALLOT`, and final `SUBMIT BALLOT` intent.

A guest selecting CREATE BALLOT authenticates first and then resumes create intent. Direct registration ends at authenticated Voter ID and does not open the form automatically.

### Authentication

Email/password login and registration share voter-registration language. Registration includes optional public display name, email, password, and password confirmation in the frontend.

Google/GitHub actions render only after provider discovery. Callback success, cancellation, linking requirement, and failure use safe catalog copy. Provider errors and credentials must never be reflected raw into the UI.

Register, login, and refresh return session plus profile. The authenticated UI should not wait for a second `/users/me` request on the normal path.

### Voter ID and profile

Authenticated masthead state displays:

- Ballot Mark;
- public display name and initials;
- role;
- optional private email or safe not-shared fallback;
- linked provider state;
- Edit Profile, View Profile, MY BALLOTS, logout, and logout-all actions.

The profile editor provides:

- large live Ballot Mark preview;
- ten icon choices;
- six color choices;
- display name and bio fields;
- preferred-language value;
- linked-provider status;
- accessible close, cancel, and save behavior.

The public profile exposes only public-safe identity. Empty bios render no default sentence. Free-form image upload is intentionally unsupported.

## 5. Ballot interaction

UP and DOWN are two ballot choices, not Reddit-style vertical arrows.

Required states:

- neutral;
- UP selected;
- DOWN selected;
- submitting;
- authoritative success;
- rollback after failure;
- rate limited;
- closed/locked;
- current verdict;
- final verdict;
- final undecided.

Server response is authoritative for:

- `voteScore`;
- `upVotes`;
- `downVotes`;
- `totalVotes`;
- `myVote`;
- `verdictThreshold`;
- `verdict`;
- lifecycle and update timestamps.

Interaction sequence:

1. Apply optimistic choice/count change in the same interaction frame.
2. Show short functional press/stamp feedback.
3. Prevent double-submit for that ballot without freezing unrelated cards.
4. Reconcile from the authoritative REST response.
5. Apply newer SSE aggregate snapshots without replacing `myVote`.
6. On failure, prefer authoritative detail reconciliation before rollback when stream state may be newer.

Do not rely on color alone. Selected state also needs a mark, outline, label, or stamp.

## 6. Ballot Mark identity

The implemented system uses ten faceless civic/editorial presets:

```text
Citizen    Advocate    Thinker     Organizer    Volunteer
Creator    Leader      Analyst     Visionary    Builder
```

Each preset is a custom monochrome 64×64 SVG rendered through a CSS mask and colored by one of:

```text
Navy  Seal  Kraft  Graphite  Moss  Ink Blue
```

Rules:

- Preserve backend enum names and database constraints.
- Keep marks visually clear at mobile sizes and in author cards.
- Use the same identity across masthead, editor preview, public profile, ballot author summary, and future comments.
- Do not accept arbitrary SVG, HTML, remote image URL, or photo upload.
- Decorative inner glyphs must not replace an accessible voter-name label.

## 7. Internationalization

Vietnamese and English system copy is implemented through typed domain catalogs. Vietnamese is the current default.

Rules:

- machine/domain values remain stable language-neutral codes;
- system labels, validation, empty states, errors, notices, accessible labels, and metadata are localized;
- user-generated content remains in the author's language;
- switching locale must not remount the application or reset feed/session/dialog state;
- both catalogs must pass key-parity checks;
- new feature copy must land in VI and EN in the same PR;
- profile `preferredLocale` is durable, but automatic application is a known gap tracked by TON-191.

Exact current runtime behavior: [`docs/I18N.md`](docs/I18N.md).

## 8. Responsive rules

Required QA widths:

- `1440 × 900`;
- `1024 × 768`;
- `768 × 1024`;
- `390 × 844`;
- `375 × 812`;
- `320 × 568`.

Mobile is first-class:

- single-column layout;
- minimum 44px important touch targets;
- no page/dialog horizontal scrolling;
- vote choices remain prominent;
- owner actions remain labelled and discoverable;
- guest actions do not wrap or compete at 320px;
- all feed modes remain visible;
- profile icon choices remain distinct and large enough to identify;
- dialogs manage internal overflow intentionally.

Desktop adds whitespace and editorial structure, not a permanent-sidebar dashboard.

## 9. Reusable implementation boundaries

Current components include:

```text
VoterMasthead
AuthDialog
BallotApp
FeedControls
BallotCard
BallotHeader
BallotOptions
BallotStamp
BallotActions
BallotDetailDialog
CreateBallotDialog
EditBallotDialog
ConfirmDialog
ProfileDialog
PublicProfileDialog
BallotMark
```

Keep styling split by component with SCSS Modules. Shared reset, tokens, typography, breakpoints, texture, torn-edge mixins, and motion utilities belong in shared SCSS sources.

API code remains separated into transport/auth/social/user/ballot domains. Do not put fetch/session logic inside presentational components.

## 10. Required states

Visually verify:

- immediate public-feed startup;
- session restore independently of public feed;
- authenticated profile bootstrap;
- empty feed and no search results;
- Redis-ranked degraded fallback;
- API unavailable;
- validation errors;
- login/register/social failure;
- account-link-required state;
- session expired;
- 403 owner-action failure;
- 404 ballot/profile not found;
- 409 lifecycle conflict;
- 429 with `Retry-After`;
- delete and close confirmation;
- optimistic vote and rollback protection;
- REST/SSE reconciliation;
- success feedback for create, update, close, delete, and profile save;
- EventSource unavailable/error fallback;
- long names, titles, content, and translated strings;
- public profile with and without bio.

## 11. Accessibility and motion

- WCAG AA contrast.
- Visible keyboard focus.
- Semantic buttons, fieldsets, legends, and labels.
- Error text associated with fields.
- Minimum 44px key touch targets.
- Dialog focus containment, restoration, Escape, and backdrop behavior.
- Reduced-motion support.
- No essential information conveyed only by color or animation.
- Accessible count/current-choice labels on vote controls.
- `aria-live` handling for notices and verdict changes.

Allowed motion is short and functional: vote press, stamp impact, counter/bar transition, dialog transition, and rollback feedback. Do not animate the initial verdict stamp. Disable nonessential motion for `prefers-reduced-motion`.

## 12. Implementation and QA

Before declaring UI work complete:

1. run frontend tests and Next.js production build;
2. execute the supported viewport matrix;
3. assert page/dialog/nested overflow boundaries;
4. verify keyboard, focus, touch targets, contrast, and reduced motion;
5. verify real API shapes, privacy boundaries, and error states;
6. update VI and EN catalogs for user-facing copy;
7. update relevant documentation or declare `Docs: N/A` with a reason;
8. verify production container/runtime smoke when shared packaging changes;
9. verify Vercel preview behavior when deployment configuration changes.
