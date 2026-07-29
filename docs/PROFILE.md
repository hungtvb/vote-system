# Identity Profile and Ballot Mark

Vote System provides an editable voter identity while keeping private account data separate from public profile data.

## Auth bootstrap and private profile

Register, login, and refresh responses contain the authenticated private profile alongside the normal session fields. The normal frontend startup path therefore restores session and profile with one request.

Explicit private retrieval remains available:

```http
GET /api/v1/users/me
Authorization: Bearer <access-token>
```

The private profile contains account-private fields such as `email`, `role`, and `linkedProviders`, plus:

- `displayName`;
- `initials`;
- optional `bio`;
- `avatarIcon`;
- `avatarColor`;
- `preferredLocale`;
- account timestamps.

A social-only account may have `email: null`.

## Update profile

```http
PATCH /api/v1/users/me
Authorization: Bearer <access-token>
Content-Type: application/json
```

```json
{
  "displayName": "Alex Voter",
  "bio": "Builds reliable public software.",
  "avatarIcon": "BUILDER",
  "avatarColor": "INK_BLUE",
  "preferredLocale": "vi"
}
```

Validation rules:

- `displayName`: required, 2–40 characters; surrounding whitespace is removed and repeated spaces are collapsed;
- `bio`: optional, maximum 160 characters;
- `avatarIcon`, `avatarColor`, and `preferredLocale`: required locked enum values;
- email, role, and linked identities are not editable through this endpoint.

Profile update is user-scoped and rate-limited. The frontend uses the refresh-safe authorized-request path and updates the active profile without reloading the page.

## Public profile

```http
GET /api/v1/users/{userId}
```

The public response contains only:

- `id`;
- `displayName`;
- `initials`;
- optional `bio`;
- `avatarIcon`;
- `avatarColor`;
- `createdAt`.

It never exposes email, role, preferred locale, password data, refresh sessions, or linked OAuth providers. When `bio` is null, empty, or whitespace-only, the public profile UI renders no placeholder sentence as user-authored content.

Ballot author summaries use the same public-safe identity boundary.

## Locked Ballot Mark presets

Icons:

```text
CITIZEN
ADVOCATE
THINKER
ORGANIZER
VOLUNTEER
CREATOR
LEADER
ANALYST
VISIONARY
BUILDER
```

Colors:

```text
NAVY
SEAL
KRAFT
GRAPHITE
MOSS
INK_BLUE
```

The frontend renders ten custom 64×64 monochrome SVG assets through CSS masks so the six backend color enums remain authoritative. The database has matching check constraints. Clients must not invent additional values without a coordinated migration and API update.

Free-form photo or SVG upload is not supported.

## Locale preference

Supported profile values are `vi` and `en`. The value is persisted and returned in auth bootstrap/private-profile responses.

Current runtime behavior:

- the language switcher applies and stores a local `vote.locale` selection;
- profile editing persists `preferredLocale`;
- the persisted profile value is not yet automatically applied during session bootstrap or immediately after profile save.

TON-191 tracks browser-language fallback and authenticated preferred-locale application. See [`I18N.md`](I18N.md) for the exact current locale strategy.

## Privacy and security rules

- Access and refresh tokens are never included in profile payloads.
- Public endpoints never expose account email, role, providers, or preferred locale.
- Avatar icon/color values are enums; arbitrary markup is rejected by design.
- Profile update uses the authenticated user ID from the security context, never a client-supplied owner ID.
