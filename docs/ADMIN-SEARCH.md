# Administrator user and ballot search

TON-197 provides read-only moderation APIs for the future admin workspace. These queries are isolated from public profile and feed repositories so unrestricted administrator visibility cannot leak into public paths.

## Authorization

Every route requires `ROLE_ADMIN` at both request and method levels:

```http
GET /api/v1/admin/users
GET /api/v1/admin/users/{userId}
GET /api/v1/admin/posts
GET /api/v1/admin/posts/{postId}
```

```text
anonymous             -> 401
authenticated USER    -> 403
restricted ADMIN      -> 401 through account-access enforcement
active ADMIN          -> allowed
```

## Stable page contract

List endpoints return an explicit DTO rather than serializing Spring Data `Page<T>`:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "first": true,
  "last": true
}
```

Page is zero-based, maximum page is 1,000,000, default size is 20, and maximum size is 100. Ordering is fixed:

```text
createdAt DESC, id DESC
```

No client-controlled sort expression is accepted.

## User search

```http
GET /api/v1/admin/users?id={uuid}&query={text}&role=ADMIN&accountStatus=ACTIVE&createdFrom={instant}&createdTo={instant}&page=0&size=20
```

Filters:

- exact `id`;
- case-insensitive email or display-name substring, maximum 200 characters;
- exact `role` (`USER` or `ADMIN`);
- effective `accountStatus` (`ACTIVE`, `SUSPENDED`, or `BANNED`);
- inclusive `createdFrom` and `createdTo` ISO-8601 instants.

Effective-status filtering matches TON-196:

- raw `ACTIVE` is active;
- an expired temporary restriction is also effective `ACTIVE`;
- `SUSPENDED`/`BANNED` include only non-expired restrictions.

`createdFrom` after `createdTo` returns `400`.

User response fields:

```text
id
email
displayName
initials
role
accountStatus          effective status
statusUntil            omitted for effective ACTIVE
statusUpdatedAt
linkedProviders        provider names only
createdAt
updatedAt
```

Excluded:

```text
passwordHash
providerSubject
provider email payload
access/refresh token or hash
session rows
IP address
raw user agent
```

Provider names are loaded through a `(userId, provider)` projection. Provider subjects are not selected for the response hydration query.

## Ballot search

```http
GET /api/v1/admin/posts?id={uuid}&ballotNumber=BAL-12345678&query={text}&authorId={uuid}&category=GENERAL&status=OPEN&moderationStatus=HIDDEN&createdFrom={instant}&createdTo={instant}&page=0&size=20
```

Filters:

- exact `id`;
- exact ballot number after uppercase normalization, maximum 32 characters;
- case-insensitive title/content substring, maximum 200 characters;
- exact `authorId`;
- exact uppercase-normalized category, maximum 50 characters;
- lifecycle status `OPEN` or `CLOSED`;
- moderation status `VISIBLE`, `HIDDEN`, or `DELETED`;
- inclusive created-at range.

Admin ballot responses include the author summary, lifecycle/moderation state, aggregate vote counts, score, verdict data, and timestamps. They do not include `myVote`, individual voter IDs, vote rows, or voter identities.

Hidden and deleted ballots are intentionally readable through these administrator routes. Public detail/feed/vote/SSE behavior remains controlled by TON-195 and continues returning generic not-found behavior.

## Search semantics

Text filters escape `\`, `%`, and `_` before SQL LIKE matching. Searching for `%` therefore finds a literal percent sign rather than every row.

Blank text/category/ballot-number parameters are treated as absent. Category and ballot number normalize with `Locale.ROOT` uppercase.

## Query design and N+1 boundary

Dedicated `EntityManager` repositories execute:

- one page-content query;
- one count query;
- one batch provider-name query for user pages, or one batch author query for ballot pages.

The account-access filter adds one authoritative user lookup for the requesting administrator. Integration tests enforce a ceiling of four prepared statements for populated list pages:

```text
account check + content + count + batch hydration
```

Adding more rows must not increase the statement count.

## Indexes

Flyway V11 adds only indexes used by implemented filters/order:

```text
users(created_at DESC, id DESC)
posts(moderation_status, created_at DESC, id DESC)
```

Existing indexes continue serving unique email, ballot number, author, and public-feed access paths. No wildcard-search index is added because `%substring%` queries do not benefit from a normal B-tree index.

## Verification

Integration coverage includes:

- anonymous and USER rejection plus direct method-security enforcement;
- effective account status including expired restrictions;
- email/display-name and title/content matching with literal wildcard escaping;
- created-at ranges and validation boundaries;
- role, lifecycle, moderation, category, author, ID, and ballot-number behavior;
- hidden/deleted admin detail with unchanged public 404 behavior;
- deterministic pagination under equal timestamps;
- response privacy-field absence;
- constant query counts with multiple providers and distinct authors;
- Flyway V11 and production runtime smoke.
