# Table-first administrator dashboard

TON-201 changes `/admin/` from public-style record cards to a dense operations dashboard.

## Runtime structure

```text
AdminPage
└─ AdminPageRouter
   └─ AdminOperationsWorkspace
      ├─ Overview
      ├─ Users table
      ├─ Ballots table
      ├─ Audit table
      └─ Ranking panel
```

All sections use one authorization gate, header, sidebar, locale switch and logout path. Ranking no longer owns a duplicate administrator shell.

## Data presentation

Desktop and tablet render semantic HTML tables with sticky headers. Mobile keeps the same operational fields in compact key-value rows instead of reusing public ballot cards.

### Users

Columns include identity, email, role, effective account status, restriction expiry, linked providers, creation time and actions.

### Ballots

Columns include ballot identity, author, category, lifecycle, moderation state, vote totals/score, creation time and actions.

### Audit log

Columns include timestamp, action, actor, target type, target, reason and expandable metadata. Metadata remains collapsed until requested so long audit datasets stay scannable.

## Interaction policy

- Search and state filters remain URL-backed and server-driven.
- Page number and page size are URL-backed; supported sizes are 12, 25, 50 and 100.
- Existing rows remain visible while a refresh request is active.
- Destructive and access-changing operations still require a bounded reason dialog.
- Unknown metrics render as `—` or `UNAVAILABLE`, never as fabricated zero values.
- Ranking rebuild remains disabled when authoritative status cannot be loaded.

## Responsive and accessibility behavior

- Table headers use `scope="col"`.
- Controls retain visible keyboard focus.
- Action menus use native `details`/`summary` semantics.
- Audit metadata uses native expandable disclosure.
- The table container may scroll horizontally on intermediate widths without causing page-level horizontal overflow.
- At narrow mobile widths each table row becomes a compact labeled record.

## Verification

The Admin Workspace QA workflow covers guest and USER access boundaries, overview layouts at 390/768/1440 px, semantic Users and Audit tables, page-size controls, the reason dialog, shared Ranking shell and page-level overflow assertions.
