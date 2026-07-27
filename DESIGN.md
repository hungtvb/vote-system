# Vote System UI Design Specification

**Theme:** Ballot Edition  
**Scope:** Current public web product  
**Implementation target:** Next.js App Router, TypeScript, SCSS Modules  
**Reference source:** `design/stitch/ballot-edition/`

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

The exported HTML is visual reference material, not production code. Production UI must preserve the visual language while being rebuilt as reusable Next.js components with semantic JSX and SCSS Modules.

When this document and the Stitch export differ, follow this priority:

1. Current backend capability and API contract
2. This `DESIGN.md`
3. Approved Stitch visual reference
4. Generated HTML implementation details

## 2. Current product scope

The public application supports:

- Email/password registration and login
- Optional Google and GitHub social login
- Explicit linked-provider state in Voter ID
- Public display name and privacy-safe author summary
- Browse, search, filter, and paginate ballots
- Feed modes: LATEST, HOT, TOP DAY, TOP WEEK, and authenticated MY BALLOTS
- Category and OPEN/CLOSED filtering
- Open ballot detail
- Create, edit, close, and delete owned ballots
- Category, closing time, and verdict-threshold fields
- Upvote, downvote, change vote, and remove vote
- Score, up/down/total counts, threshold, and server verdict
- Realtime aggregate convergence in the open ballot dialog through SSE
- Logout and logout-all
- Loading, empty, validation, authentication, rate-limit, optimistic, rollback, and degraded-network states

Not implemented in the current public product:

- Free-form avatar/photo upload
- Editable bio and Ballot Mark presets
- Comments and replies
- Notification inbox
- Chat/direct messaging
- Public bookmarks
- File attachments or ballot images
- Admin/moderation screens

These are roadmap items, not UI placeholders. Do not expose inert controls before backend contracts exist.

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

### Typography

- Headings: Playfair Display or similar editorial serif
- Body and controls: Public Sans or similar readable humanist sans
- Scores and metadata: JetBrains Mono or similar monospace

Use sharp or nearly sharp corners, structural borders, restrained paper texture, and no soft glass shadows.

## 4. Current screens

### Feed

The feed contains:

- Ballot Edition masthead with logo, search, create-ballot action, language-ready structure, and guest/Voter ID state
- Social login actions only for providers enabled by backend discovery
- Feed mode controls backed by server-owned results
- Query, category, and status filtering
- Compact ballot cards with author, timestamp, ballot number, category, title, excerpt, counts, verdict, choices, and owner actions
- Loading, empty, error, result-count, current-page, and load-more states

Public LATEST/HOT/TOP content begins loading before refresh-session restoration completes. MY BALLOTS waits for authenticated identity. UI must not blank the public feed while auth restore is running.

### Ballot detail

Show full content, author metadata, lifecycle state, timestamps, category, closing time, vote totals, current user's REST-owned vote, threshold, verdict, and owner actions.

For an active ballot, the open detail dialog subscribes to one SSE stream. Shared aggregate updates may change counts/verdict; personal `myVote` remains owned by authenticated REST state.

Do not add comments, related content, or analytics until those contracts are implemented.

### Create and edit ballot

Use official-submission language consistent with the Ballot Edition concept.

Supported fields:

- Title
- Full content
- Category
- Closing time
- Verdict threshold

Required behavior:

- client and server validation;
- disabled/submitting state;
- cancel;
- create/update action;
- delete confirmation for owned records;
- close action for active records;
- unsaved-change protection where practical;
- clear distinction between `REGISTER`, `CREATE BALLOT`, and final `SUBMIT BALLOT` intent.

A guest who selects CREATE BALLOT authenticates first, then resumes the pending create flow. Direct registration ends at authenticated Voter ID and must not open the ballot form automatically.

### Authentication

Email/password login and registration share voter-registration visual language. Registration includes optional public display name, email, password, and password confirmation in the frontend.

Google/GitHub buttons render only after provider discovery confirms availability. Social callback success, cancellation, account-link requirement, and failure must use safe local copy. Provider errors must never be reflected raw into UI.

### Voter ID

Authenticated masthead state displays:

- public display name;
- initials;
- role;
- optional private email or safe not-shared fallback;
- linked provider state;
- logout and logout-all actions.

The disclosure uses accessible native/menu semantics and remains usable at mobile widths. Future editable profile/Ballot Mark work must extend this identity model rather than replace it with an unrelated avatar style.

## 5. Ballot interaction

UP and DOWN are two ballot choices, not Reddit-style vertical arrows.

Required states:

- Neutral
- UP selected
- DOWN selected
- Submitting
- Authoritative success response
- Rollback after failure
- Rate limited
- Closed/locked
- Current verdict
- Final verdict
- Final undecided

Server response is authoritative for:

- `voteScore`
- `upVotes`
- `downVotes`
- `totalVotes`
- `myVote`
- `verdictThreshold`
- `verdict`
- lifecycle and update timestamps

Interaction sequence:

1. Apply optimistic choice/count change.
2. Show short functional press/stamp feedback.
3. Reconcile from authoritative REST response.
4. Apply newer SSE aggregate snapshots without replacing `myVote`.
5. On failure, refetch authoritative detail before rollback when a newer stream event may exist.

Do not rely on color alone. Selected state must also use a check, punched mark, label, or stamp.

## 6. Responsive rules

Required QA widths:

- `1440 × 900`
- `1024 × 768`
- `768 × 1024`
- `390 × 844`
- `375 × 812`
- `320 × 568`

Mobile is first-class:

- single-column layout;
- minimum 44px important touch targets;
- no page/dialog horizontal scrolling;
- vote choices remain prominent;
- owner actions remain labelled and discoverable;
- guest actions do not wrap or compete at 320px;
- all feed modes, including MY BALLOTS, remain visible;
- dialogs fit the viewport and manage internal overflow intentionally.

Desktop should add whitespace and editorial structure, not become a permanent-sidebar dashboard.

## 7. Reusable implementation boundaries

Current component direction includes:

```text
VoterMasthead
AuthDialog
BallotApp
BallotCard
BallotHeader
BallotOptions
BallotStamp
BallotActions
BallotDetailDialog
CreateBallotDialog
EditBallotDialog
ConfirmDialog
LoadingState
EmptyState
ErrorState
LoadMore
```

Keep styling split by component with SCSS Modules. Shared reset, tokens, typography, breakpoints, paper texture, torn-edge mixins, and motion utilities belong in dedicated shared SCSS sources.

API code remains separated into transport/auth/social/user/ballot domains. Do not place fetch/session logic directly inside presentational components.

## 8. Required states

Implement and visually verify:

- Feed loading and immediate public-feed startup
- Session restore running independently of public feed
- Empty feed and no search results
- Redis-ranked feed degraded fallback
- API unavailable
- Form validation errors
- Login/register/social failure
- Account-link-required state
- Session expired
- 403 owner-action failure
- 404 ballot not found
- 409 closed/expired stream or lifecycle conflict
- 429 with `Retry-After`
- Delete and close confirmations
- Vote optimistic state
- REST/SSE reconciliation
- Vote rollback protection
- Success feedback for create, update, close, and delete
- EventSource unavailable/error fallback
- Long display name, title, and content

## 9. Accessibility and motion

- WCAG AA contrast
- Visible keyboard focus
- Semantic buttons and form labels
- Error text associated with fields
- Minimum 44px key touch targets
- Dialog focus containment and keyboard dismissal
- Reduced-motion support
- No essential information conveyed only by color or animation
- Accessible count/percentage/current-choice labels on vote controls
- `aria-live` handling for verdict transitions

Allowed motion is short and functional: vote press, stamp impact, counter/bar transition, dialog transition, and rollback feedback. Do not animate the initial verdict stamp. Disable nonessential motion for `prefers-reduced-motion`.

## 10. Internationalization direction

TON-116 introduces Vietnamese and English system UI before Profile, Admin, Comments, and Notifications expand copy volume.

Rules:

- machine/domain values remain stable language-neutral codes;
- system labels, validation, empty states, errors, accessible labels, and metadata are localized;
- user-generated ballot content remains in the language entered by the author;
- switching locale must not reset feed, session, or active dialog unnecessarily;
- both locale catalogs must pass key-parity and long-string visual QA.

Do not hard-code new user-facing system copy in feature components after the i18n foundation lands.

## 11. Future Ballot Mark

The planned profile system uses a curated set of ten faceless civic/editorial avatar presets plus an approved color palette. It should feel like a personal ballot mark, not a generic photo uploader.

Initial direction:

- Citizen
- Advocate
- Thinker
- Organizer
- Volunteer
- Creator
- Leader
- Analyst
- Visionary
- Builder

The avatar system must reuse Navy, Seal, Kraft, Graphite, Moss, and Ink Blue variants and remain visually consistent in Voter ID, author summaries, future comments, and profile pages.

Free-form photo upload is out of scope for the first profile release.

## 12. Implementation and QA notes

The Stitch HTML uses generated utility markup. Do not copy it directly into production. Rebuild with semantic JSX, reusable components, typed API models, and SCSS Modules.

Before declaring UI work complete:

1. run frontend tests and Next.js production build;
2. execute the six-viewport visual matrix;
3. assert page and dialog overflow boundaries;
4. verify keyboard, focus, touch targets, contrast, and reduced motion;
5. verify real API response shapes and error states;
6. verify the production container/runtime smoke when shared frontend/backend packaging is affected;
7. verify Vercel preview behavior when deployment configuration changes.