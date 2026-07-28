# Identity Profile and Ballot Mark

TON-108 adds an editable voter identity while keeping private account data separate from the public profile.

## Private profile

Authenticated endpoint:

```http
GET /api/v1/users/me
Authorization: Bearer <access-token>
```

The response includes account-private fields such as `email`, `role`, and `linkedProviders`, plus the editable identity fields:

- `displayName`
- `bio`
- `avatarIcon`
- `avatarColor`
- `preferredLocale`

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

- `displayName`: required, 2–40 characters; surrounding whitespace is removed and repeated spaces are collapsed.
- `bio`: optional, maximum 160 characters.
- `avatarIcon`, `avatarColor`, and `preferredLocale`: required locked enum values.
- Email, role, and linked identities are not editable through this endpoint.

The update is user-scoped and rate-limited. The frontend sends it through the refresh-safe authorized-request path, then updates the active profile without reloading the page.

## Public profile

Public endpoint:

```http
GET /api/v1/users/{userId}
```

The response contains only:

- `id`
- `displayName`
- `initials`
- `bio`
- `avatarIcon`
- `avatarColor`
- `createdAt`

It never exposes email, role, preferred locale, password data, refresh sessions, or linked OAuth providers.

## Locked avatar presets

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

The database has matching check constraints. Clients must not invent additional values without a coordinated migration and API update.

## Locale behavior

Supported values are `vi` and `en`.

- Anonymous users keep the browser-local locale preference.
- When an authenticated profile is loaded, its backend preference is applied once for that user session.
- Users can still switch locale locally during the session.
- Saving the profile persists and immediately applies the selected locale.
