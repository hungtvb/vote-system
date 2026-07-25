# Vote System UI Design Specification

**Theme:** Ballot Edition  
**Scope:** Public web MVP only  
**Implementation target:** Next.js, TypeScript, SCSS Modules  
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

The exported HTML is a visual reference, not production code. Production FE must preserve the visual language while being rebuilt into reusable Next.js components and SCSS Modules.

When this document and the Stitch HTML differ, follow this priority:

1. Current backend capability and API contract
2. This `DESIGN.md`
3. Approved Stitch visual reference
4. Generated HTML implementation details

## 2. Product scope

The public MVP supports:

- Register and login
- Browse and search posts
- Feed modes already exposed by the backend
- Open post detail
- Create, edit, and delete owned posts
- Upvote, downvote, change vote, and remove vote
- Display score, up-vote count, down-vote count, total votes, and server verdict
- Logout and logout all sessions
- Loading, empty, validation, authentication, rate-limit, and vote rollback states

Do not add unsupported features:

- Post images or uploads
- Comments
- Tags or categories
- Bookmarks
- Social sharing
- Notifications or messaging
- Admin screens in this phase

## 3. Visual direction

Ballot Edition combines printed ballots, editorial layouts, filing cards, stamps, perforated rules, serial numbers, and analog counters.

The product should feel trustworthy, deliberate, editorial, modern, lightweight, and slightly tactile. It must not resemble a generic SaaS dashboard, Material UI template, Bootstrap page, crypto product, or glassmorphic interface.

### Core palette

| Token | Value | Usage |
|---|---:|---|
| Navy | `#1E2A3A` | primary actions, structure, UP state |
| Seal Red | `#B8342E` | DOWN state, destructive action, errors |
| Kraft | `#C9A876` | tabs, separators, secondary surfaces |
| Bone | `#F0E9D8` | page and paper background |
| Graphite | `#3A362E` | body text and neutral borders |

### Typography

- Headings: Playfair Display or a similar editorial serif
- Body and controls: Public Sans or a similar readable humanist sans
- Scores and metadata: JetBrains Mono or a similar monospace

Use sharp or nearly sharp corners, structural borders, restrained paper texture, and no soft glass shadows.

## 4. Approved screens

### Feed

The feed contains:

- Header with logo, search, Create Post, and login/profile state
- Feed mode controls supported by the API
- Compact post cards with author, timestamp, title, excerpt, score, vote totals, verdict, vote control, and owner actions
- Pagination or Load More based on the API

The approved Stitch feed is the visual baseline, but production cards should be about 20–25% more compact so desktop shows several posts per viewport.

### Post detail

Show full content, author metadata, timestamps, vote totals, current user vote, server verdict, and owner actions. The vote panel is the primary focal area. Do not add comments, related content, or analytics unless later supported.

### Create and edit post

Use the same official-submission form language. Fields are only title and content. Include validation, disabled/loading state, cancel, publish/save, delete confirmation for edit, and unsaved-change protection where practical.

### Login and register

Reuse the voter-registration visual language. Login uses email and password. Register uses email, password, and confirm password in the UI. Include validation, API errors, session-expired state, and links between login and register.

## 5. Vote interaction

UP and DOWN are two ballot choices, not Reddit-style vertical arrows.

Required states:

- Neutral
- UP selected
- DOWN selected
- Submitting
- Authoritative success response
- Rollback after failure
- Rate limited

The server response is authoritative and may include:

- `voteScore`
- `upVotes`
- `downVotes`
- `totalVotes`
- `myVote`
- `verdictThreshold`
- `verdict`

Interaction sequence:

1. Apply optimistic selection and counter change.
2. Show a short press/stamp response.
3. Reconcile all vote fields from the server response.
4. On failure, restore prior state and show a concise message.

Do not rely on color alone. Selected state must also use a check, punched mark, label, or stamp.

## 6. Responsive rules

Target widths:

- Mobile: `375px`
- Tablet: `768px`
- Desktop: `1440px`

Mobile is first-class:

- Single-column layout
- Minimum 44px touch targets
- No horizontal scrolling
- Vote controls remain prominent
- Owner actions move into a labelled overflow menu when needed
- Header actions collapse without hiding search or Create Post

Desktop should add whitespace and editorial structure, not turn into a dashboard. Do not introduce a permanent sidebar for the public MVP.

## 7. Reusable components

Suggested component set:

```text
AppHeader
SearchField
FeedModeTabs
PostCard
PostMetadata
VoteControl
VoteCounter
VerdictStamp
PostForm
AuthForm
UserMenu
ConfirmDialog
LoadingSkeleton
EmptyState
ErrorBanner
Toast
Pagination
```

Keep styles split by component using SCSS Modules. Shared design tokens, typography, reset, breakpoints, and motion utilities belong in dedicated SCSS files.

## 8. Required states

Implement and visually verify:

- Feed loading and skeleton
- Empty feed
- No search results
- API unavailable
- Form validation errors
- Login/register failure
- Session expired
- 403 owner-action failure
- 404 post not found
- 429 with Retry-After
- Delete confirmation
- Vote optimistic state
- Vote rollback
- Success feedback for create, update, and delete

## 9. Accessibility and motion

- WCAG AA contrast
- Visible keyboard focus
- Semantic buttons and form labels
- Error text associated with fields
- Minimum 44px touch targets
- Reduced-motion support
- No essential information conveyed only through animation or color

Allowed motion is short and functional: vote press, stamp impact, counter tick, dialog transition, and rollback. Avoid decorative entrance animation.

## 10. Implementation notes

The Stitch HTML uses utility classes and generated markup. Do not copy it directly into production. Rebuild it with semantic JSX, reusable components, typed API models, and SCSS Modules.

Before declaring UI complete, compare the implementation against the approved Stitch reference at 375px, 768px, and 1440px, and verify all API-driven states with real response shapes.
