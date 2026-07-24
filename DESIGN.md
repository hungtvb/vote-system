# Vote System UI Design Specification

**Design theme:** Ballot Edition  
**Primary implementation target:** Next.js, TypeScript, SCSS Modules  
**Design source:** Google Stitch project and this repository specification

## 1. Product design goal

Vote System is a social voting product where users create posts, browse a public feed, vote up or down, inspect results, and manage their own content. Administrators review posts, users, voting activity, moderation cases, system health, and security-related events.

The interface must feel deliberately designed rather than generated from a generic dashboard template.

The product should feel:

- trustworthy
- editorial
- deliberate
- civic-inspired without appearing political or government-owned
- slightly playful
- modern with subtle analog references
- suitable for a real production application

The product must not feel:

- like a generic SaaS dashboard
- like a crypto or fintech interface
- glossy or glassmorphic
- military or aggressive
- overly retro
- dependent on large decorative gradients

## 2. Visual language: Ballot Edition

The visual system is inspired by printed ballots, election forms, filing cards, official stamps, perforated paper, serial numbers, audit ledgers, and analog vote counting.

Use these references as visual cues, not as literal decoration on every surface.

Preferred visual details:

- restrained paper texture
- perforated or torn dividers
- numbered sections
- serial-number metadata
- punched or checked vote states
- stamp-style status labels
- paper slips and filing-card menus
- ledger-like tables
- asymmetrical editorial spacing
- subtle layered paper surfaces

Avoid excessive rounded cards. Most panels should use restrained corner radii or squared edges. Decorative details must never reduce readability or interaction speed.

## 3. Color system

### Core palette

| Token | Value | Usage |
|---|---:|---|
| Kraft | `#C9A876` | paper accents, secondary surfaces, dividers |
| Navy | `#1E2A3A` | primary actions, upvote state, strong text |
| Seal Red | `#B8342E` | downvote state, danger actions, stamped alerts |
| Bone | `#F0E9D8` | main application background and paper surfaces |
| Graphite | `#3A362E` | body text, borders, neutral controls |

White may be used sparingly for contrast. Do not introduce bright purple, neon colors, or large gradients.

### Semantic use

- **UP vote:** Navy
- **DOWN vote:** Seal Red
- **Primary background:** Bone
- **Secondary paper surface:** light Bone/Kraft variation
- **Primary text:** Graphite or Navy
- **Muted metadata:** desaturated Graphite
- **Success/approved:** Navy with explicit icon or text
- **Warning:** Kraft with dark text
- **Error/danger:** Seal Red

Color must never be the only state indicator.

## 4. Typography

Use a three-part type system:

1. **Display/headings:** condensed display, slab serif, or restrained stencil style
2. **Body:** readable humanist sans-serif or editorial serif
3. **Metadata/counters:** monospaced font

Requirements:

- headings should have strong character without compromising readability
- scores and percentages should resemble mechanical counters or printed serial numbers
- identifiers, timestamps, and audit values should use monospace
- body text must remain comfortable for long-form post content
- avoid using decorative display fonts for labels, forms, or dense admin tables

## 5. Layout principles

- mobile-first
- strong editorial hierarchy
- readable content widths
- generous outer margins on desktop
- large, intentional voting controls
- clear ownership, authentication, and moderation states
- consistent reusable component system
- no horizontal scrolling at supported breakpoints

### Supported breakpoints

- Mobile: `375px`
- Tablet: `768px`
- Desktop: `1440px`

### Desktop public layout

- narrow top navigation
- main editorial feed column
- optional slim contextual sidebar
- generous page margins
- layered paper-like content regions
- feed width remains readable rather than expanding across the full screen

### Mobile public layout

- compact header
- search and feed controls below header
- single-column feed
- prominent Create Post action
- full-width vote interaction
- owner actions moved into a compact labelled menu when necessary

## 6. Public application

### 6.1 Header

The public header contains:

- Vote System seal-style wordmark
- Feed navigation
- Search access
- Create Post action
- Login or user profile area

Mobile behavior:

- collapse navigation cleanly
- preserve direct access to search
- keep Create Post prominent
- do not use tiny icon-only actions

### 6.2 Public feed

The feed toolbar contains:

**Sort:**

- Latest
- Top
- Controversial

**Filters:**

- All
- Voted
- My Posts

The toolbar may use tabs, paper tags, or compact ballot selectors. Controls must clearly indicate the active state.

### 6.3 Post card

Each post card must show:

- author avatar or initials
- author name
- relative timestamp
- ownership indicator when applicable
- post title
- short content preview
- current score
- upvote count
- downvote count
- current user vote state
- discussion-count placeholder
- edit/delete actions for the owner
- clickable path to post detail

The post card must not look like a generic social-media card. Prefer an editorial composition with divided ballot regions, paper layers, serial numbers, or asymmetrical spacing.

### 6.4 Vote control

Do not use traditional Reddit-style vertical arrows.

Design UP and DOWN as competing ballot selections or paper regions.

Requirements:

- UP uses Navy
- DOWN uses Seal Red
- selected state includes a check, punch, seal, or stamped label
- score remains clearly visible between or near the choices
- controls are large and thumb-friendly on mobile
- minimum touch target is `44px`
- optimistic updates respond immediately
- failed optimistic updates visibly roll back

Suggested interaction:

1. user selects UP or DOWN
2. selected region depresses or receives a punch/check state
3. score counter ticks to the new value
4. a short `VERDICT` stamp appears
5. on API failure, the score and vote state roll back with a short restrained animation

Support reduced motion.

### 6.5 Post detail

The post detail page contains:

- Back to Feed action
- complete post content
- author metadata
- large vote interface as the visual focus
- vote breakdown
- current-user vote state
- score history or activity placeholder
- edit/delete controls for the owner
- related or trending posts
- empty discussion state

### 6.6 Create post

The create flow should feel like completing an official submission form.

Fields:

- title
- content
- character count
- validation messages
- cancel
- publish

Use numbered sections, clear labels, helper rules, disabled/loading states, and a stamped `SUBMITTED` confirmation.

### 6.7 Edit post

Reuse the create form structure while showing:

- existing-record state
- last updated metadata
- Save Changes
- Cancel
- Delete Post as a secondary danger action
- unsaved changes warning

### 6.8 Authentication

Create screens for:

- Login
- Register
- Session expired

Login fields:

- email
- password
- session explanation
- login action
- registration link
- validation and API errors

Register fields:

- email
- password
- confirm password
- password guidance
- register action
- login link

The visual direction should resemble secure voter registration paperwork without feeling bureaucratic or dated.

### 6.9 User menu

Use a compact filing-card or paper-slip treatment containing:

- user email or display name
- My Posts
- Voted Posts
- Logout
- Logout All Sessions

## 7. Public application states

Design explicit states for:

- feed loading
- no posts
- no search results
- vote submitting
- vote failed and rolled back
- API unavailable
- rate limited
- session expired
- post deleted
- permission denied

The rate-limited state must show a clear Retry-After value without appearing alarming.

Loading states should preserve the structure of the content being loaded.

## 8. Admin application

The admin application is a separate operational experience using the same Ballot Edition design system.

It should feel like the control layer of the same product, not a separate Material UI or Bootstrap dashboard.

Visual references:

- election control rooms
- ballot counting sheets
- filing registers
- audit ledgers
- official review forms
- numbered case records

The admin UI may be denser than the public UI but must remain readable and calm.

### 8.1 Admin navigation

Include:

- Control Desk label
- Overview
- Posts
- Users
- Votes
- Moderation Queue
- Rate Limits
- Audit Log
- System Health
- Settings
- Return to Public Site
- administrator profile
- logout

Desktop may use a compact left navigation rail. Tablet and mobile use a collapsible menu.

### 8.2 Admin overview

Show operational metrics:

- total users
- active users
- total posts
- posts created today
- total votes
- votes cast today
- upvote/downvote ratio
- flagged posts
- pending moderation cases
- rate-limit rejections
- authentication failures
- API health
- PostgreSQL status
- Redis status

Also include:

- recent activity
- moderation queue preview
- most active posts
- most active users
- vote activity over time
- system warning area
- quick actions

Use ledger-style metrics, stamped statuses, simple readable charts, and mechanical counters. Avoid excessive pie charts.

### 8.3 Post management

The posts view must support search, filters, sorting, and pagination or progressive loading.

Fields:

- post serial number
- title
- author
- created date
- last updated date
- score
- upvotes
- downvotes
- publication status
- report count
- moderation state
- actions

Filters:

- All
- Published
- Flagged
- Hidden
- Deleted
- High Activity
- Controversial
- Created Date
- Author
- Score Range

Actions:

- View Post
- View Public Page
- Edit Metadata
- Hide
- Restore
- Delete
- Lock Voting
- Unlock Voting
- Open Moderation Case
- View Vote Activity
- View Audit History

Destructive or moderation actions require a reason and confirmation.

Stamp states:

- APPROVED
- HIDDEN
- RESTORED
- REMOVED
- UNDER REVIEW

### 8.4 Moderation detail

The detailed review workspace contains:

- full post content
- author information
- current public status
- vote totals and distribution
- score history
- recent vote activity
- reports and moderation signals
- previous decisions
- audit records
- user-history summary
- internal moderator notes

Actions:

- Approve
- Hide
- Restore
- Remove
- Lock Voting
- Suspend Author
- Add Internal Note
- Escalate Case

Every moderation action requires a reason. The confirmation step must explain its effect.

Separate public content, system evidence, and internal notes visually.

### 8.5 User management

Fields:

- user ID
- email or display name
- account status
- registration date
- last activity
- post count
- vote count
- reports received
- active sessions
- role
- actions

Filters:

- Active
- Suspended
- Disabled
- Administrators
- Moderators
- Recently Registered
- High Activity
- Reported Users

User detail includes:

- profile information
- account status
- roles and permissions
- recent posts
- recent votes
- active refresh sessions
- login-history placeholder
- moderation history
- audit timeline

Actions:

- Suspend User
- Unsuspend User
- Disable Account
- Revoke One Session
- Revoke All Sessions
- Assign Role
- Remove Role
- View Public Activity

### 8.6 Vote administration

The vote-inspection view supports operational debugging and abuse analysis.

Fields:

- vote ID
- user
- post
- vote type
- created date
- last changed date
- current state
- source/client metadata placeholder
- related rate-limit events

Filters:

- UP
- DOWN
- Changed Votes
- Removed Votes
- Date Range
- User
- Post
- Suspicious Activity

Aggregate panels may show:

- vote velocity
- repeated vote changes
- sudden vote spikes
- abnormal voting behavior
- up/down balance

Sensitive technical details should use progressive disclosure.

### 8.7 Moderation queue

Each case shows:

- case number
- related post or user
- trigger reason
- priority
- created time
- assigned moderator
- current status
- time waiting
- actions

Statuses:

- NEW
- IN REVIEW
- ESCALATED
- RESOLVED
- DISMISSED

Priorities:

- LOW
- NORMAL
- HIGH
- URGENT

Provide both queue-list and case-detail views.

Case detail supports:

- evidence review
- moderator notes
- action history
- assignment
- escalation
- resolution
- resolution reason

### 8.8 Rate-limit operations

Show rules for:

- login
- registration
- token refresh
- create post
- vote

For every rule display:

- request limit
- time window
- enabled state
- fail-open state
- allowed requests
- rejected requests
- error count
- recent spikes

Recent rejection records include:

- timestamp
- rule
- subject type
- endpoint
- Retry-After
- result

Warn clearly that configuration changes can affect availability and abuse protection.

### 8.9 Audit log

The audit log is immutable in appearance and behavior.

Each row contains:

- timestamp
- actor
- action
- entity type
- entity identifier
- previous state
- new state
- reason
- correlation ID
- source

Filters:

- actor
- action
- entity type
- date
- moderation action
- security action
- system event

Use monospace for identifiers and structured payloads. Audit records must never be editable.

### 8.10 System health

Monitor:

- API
- PostgreSQL
- Redis
- authentication
- cache
- rate limiter
- background jobs placeholder
- OpenAPI documentation
- frontend delivery

Each service displays:

- state
- last checked
- response time
- error message
- dependency
- recommended action

Statuses:

- HEALTHY
- DEGRADED
- UNAVAILABLE
- UNKNOWN

Include incidents, recovery timeline, refresh action, technical-details link, and environment label.

### 8.11 Admin settings

Sections:

- application identity
- public registration
- post creation permissions
- voting availability
- moderation defaults
- session policy
- refresh-token policy
- rate-limit policy
- maintenance mode
- feature flags
- admin roles

Dangerous changes require:

- explicit warning
- reason
- confirmation checkbox or typed confirmation
- final confirmation

### 8.12 Admin permissions

Design role-aware states for:

- Administrator
- Moderator
- Read-only Operator

Do not silently hide every unavailable action. Disabled actions should explain why the current role cannot use them.

Also design:

- unauthorized admin access
- session expired
- insufficient permission
- empty moderation queue
- no audit results
- degraded system
- failed admin action
- successful admin action

## 9. Responsive admin behavior

### Desktop: 1440px

- compact navigation rail
- dense but readable tables
- split-pane review layouts where useful
- sticky filters or action bars where useful
- restrained readable content widths

### Tablet: 768px

- collapse secondary details
- prioritize essential columns
- open record details in side panels
- preserve access to moderation actions

### Mobile: 375px

- replace compressed tables with stacked record cards
- preserve identifier, status, owner, and primary action
- place secondary actions inside a labelled menu
- separate destructive actions from routine actions
- open filters in a full-screen sheet
- avoid horizontal scrolling

## 10. Component system

Create reusable components for:

- PublicHeader
- AdminShell
- SearchField
- FeedToolbar
- SortTabs
- FilterChips
- PostCard
- VoteControl
- ScoreCounter
- AuthorMetadata
- OwnershipBadge
- StampStatus
- LoadingSkeleton
- EmptyState
- ErrorBanner
- Modal
- ConfirmDialog
- FormField
- UserMenu
- DataTable
- RecordCard
- FilterSheet
- MetricLedger
- HealthStatus
- AuditEntry
- ModerationCaseCard
- Pagination or LoadMore

Component naming may change in implementation, but behavior and visual semantics should remain consistent.

## 11. Interaction rules

Allowed motion:

- stamp impact when voting or confirming moderation
- mechanical score tick
- paper press or punch on vote selection
- restrained rollback animation
- smooth menu and filter-sheet transitions

Avoid:

- bouncing decorative elements
- delayed interactions
- long entrance animations
- animation that blocks input

All motion must respect `prefers-reduced-motion`.

## 12. Accessibility requirements

- minimum `44px` touch targets
- strong keyboard navigation
- visible focus states
- no color-only states
- text labels for important actions
- explicit form errors beside related fields
- table rows usable without hover
- confirmation dialogs name the affected entity
- destructive actions explain consequences
- sufficient text contrast
- reduced-motion support
- semantic headings and landmark structure

## 13. Content guidelines

Use realistic sample data rather than lorem ipsum.

Sample records should include:

- realistic post titles and excerpts
- names or emails
- timestamps
- post and case IDs
- moderation reasons
- vote counts
- health statuses
- rate-limit values
- audit events

The wording should remain neutral and avoid framing the product as a political platform.

## 14. Stitch output priority

Generate and refine screens in this order:

1. Mobile public feed at 375px
2. Post card and vote interaction
3. Desktop public feed at 1440px
4. Post detail
5. Create and edit flows
6. Authentication
7. Admin overview
8. Post management
9. Moderation detail
10. User management
11. Moderation queue
12. Audit log
13. System health
14. Rate-limit operations
15. Admin settings
16. Tablet and mobile admin states

## 15. Implementation constraints

The final UI must be practical to implement with:

- Next.js
- TypeScript
- SCSS Modules
- component-level styles split by feature
- reusable design tokens
- accessible semantic HTML

Do not assume Tailwind, Material UI, Bootstrap, or a generic component library.

Prefer feature-oriented modules such as:

```text
src/
├── app/
├── features/
│   ├── auth/
│   ├── feed/
│   ├── posts/
│   ├── voting/
│   ├── admin/
│   └── moderation/
├── components/
├── styles/
│   ├── _tokens.scss
│   ├── _typography.scss
│   ├── _mixins.scss
│   └── globals.scss
└── types/
```

Stitch should treat this file as the authoritative visual and UX specification for the Vote System interface.