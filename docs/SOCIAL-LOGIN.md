# Google and GitHub social login

Vote System supports optional Google OpenID Connect and GitHub OAuth2 login while preserving the existing application session model:

- provider authorization-code exchange is handled only by Spring Security;
- provider access tokens never enter frontend JavaScript;
- provider access and refresh tokens are not persisted;
- a successful callback creates the normal Vote System refresh session;
- frontend startup restores the application through `POST /api/v1/auth/refresh` and `GET /api/v1/users/me`;
- email/password registration and login remain available when no social provider is configured.

## Active production configuration

Current topology:

```text
Frontend: https://app.ballotbox.io.vn
Backend:  https://api.ballotbox.io.vn
```

Railway variables:

```env
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
GITHUB_CLIENT_ID=
GITHUB_CLIENT_SECRET=
SOCIAL_LOGIN_SUCCESS_URL=https://app.ballotbox.io.vn/
SOCIAL_LOGIN_FAILURE_URL=https://app.ballotbox.io.vn/
OAUTH_SESSION_TIMEOUT=10m
OAUTH_SESSION_COOKIE_NAME=vote_oauth
OAUTH_SESSION_COOKIE_SECURE=true
OAUTH_SESSION_COOKIE_SAME_SITE=Lax
REFRESH_COOKIE_SECURE=true
REFRESH_COOKIE_SAME_SITE=Lax
CORS_ALLOWED_ORIGINS=https://app.ballotbox.io.vn
```

Exact production callback URLs:

```text
https://api.ballotbox.io.vn/login/oauth2/code/google
https://api.ballotbox.io.vn/login/oauth2/code/github
```

Do not configure wildcard callbacks. Provider registrations are enabled only when both client ID and client secret are present.

## Provider discovery

```http
GET /api/v1/auth/social/providers
```

```json
{
  "providers": ["google", "github"]
}
```

An installation with no provider secrets returns an empty list and continues to support email/password authentication. Starting a disabled provider returns `404`.

## Local callback URLs

For backend development on port `8080`:

```text
http://localhost:8080/login/oauth2/code/google
http://localhost:8080/login/oauth2/code/github
```

Local redirect/cookie settings:

```env
SOCIAL_LOGIN_SUCCESS_URL=http://localhost:3000/
SOCIAL_LOGIN_FAILURE_URL=http://localhost:3000/
OAUTH_SESSION_COOKIE_SECURE=false
OAUTH_SESSION_COOKIE_SAME_SITE=Lax
REFRESH_COOKIE_SECURE=false
REFRESH_COOKIE_SAME_SITE=Lax
CORS_ALLOWED_ORIGINS=http://localhost:3000
```

## Scopes and provider identity

Google uses OpenID Connect scopes:

```text
openid profile email
```

Spring Security stores the authorization request in the temporary OAuth session, validates `state`, adds OIDC nonce, and validates provider response through the framework.

GitHub uses:

```text
read:user
```

Vote System does not require `user:email`. GitHub private or missing email is supported because the durable GitHub provider subject identifies the local user. Provider email is stored only when returned and is never treated as verified for automatic account ownership decisions.

## Browser entry points

Public authentication:

```http
POST /api/v1/auth/social/google/start
Content-Type: application/json

{"intent":"authenticate"}
```

Pending create-ballot continuation:

```json
{"intent":"create-ballot"}
```

The backend returns its own `/oauth2/authorization/{provider}` URL. Callers cannot supply arbitrary return URLs.

Authenticated linking:

```http
POST /api/v1/auth/social/github/link/start
Authorization: Bearer <access-token>
```

The link-start endpoint uses normal bearer authentication. The local user is captured in temporary OAuth context before redirecting to the provider.

## Temporary OAuth session

JWT bearer authentication remains the application session mechanism. An HTTP session exists only during the authorization-code round trip.

```env
OAUTH_SESSION_TIMEOUT=10m
OAUTH_SESSION_COOKIE_NAME=vote_oauth
OAUTH_SESSION_COOKIE_SECURE=true
OAUTH_SESSION_COOKIE_SAME_SITE=Lax
```

The temporary session stores only Spring Security authorization request state and allowlisted Vote System intent. It is invalidated after success or failure.

A separate Vote System refresh cookie is issued after successful social login or link. The JWT `SecurityContext` is request-scoped and is not persisted in the HTTP session.

For a future cross-site deployment using different registrable domains, both temporary OAuth and refresh cookies may require:

```env
OAUTH_SESSION_COOKIE_SECURE=true
OAUTH_SESSION_COOKIE_SAME_SITE=None
REFRESH_COOKIE_SECURE=true
REFRESH_COOKIE_SAME_SITE=None
```

Use `SameSite=None` only over HTTPS with explicit CORS origins. The current `app.ballotbox.io.vn` / `api.ballotbox.io.vn` topology is same-site and uses `Lax`.

## Local identity model

Flyway V6 adds `user_identities` with uniqueness guarantees:

- `(provider, provider_subject)` identifies exactly one local user;
- `(user_id, provider)` prevents one local user from linking multiple identities from the same provider.

A social-only local user may have no email and no password hash. Private profile APIs model email as optional. The Voter ID displays a safe fallback when email is not shared.

Repeated login with the same provider subject resolves the same local user and does not duplicate ballots, votes, roles, or profile ownership.

## Safe account linking

Vote System never silently merges accounts by email.

- Existing provider subject: resolve its linked local user.
- New subject without a verified local-account collision: create a new local user.
- Google verified email matching an existing local account: return `account_link_required`.
- The user signs into the existing account and explicitly chooses `LINK GOOGLE` or `LINK GITHUB`.
- A provider subject already linked to another local user cannot be moved through login.
- A second identity for the same provider cannot be linked to one local user.
- GitHub public email is not trusted as verified for automatic linking.

No unlink endpoint exists in the current release, preventing accidental removal of the last usable login method.

## Callback results

Success and failure redirect to fixed configured frontend URLs. Query parameters contain only allowlisted status values and codes:

```text
/?social=success&provider=google&intent=create-ballot
/?social=linked&provider=github&intent=link-account
/?social=error&code=access_denied&intent=authenticate
```

The URL never contains application access/refresh tokens, provider tokens, authorization code, email, or provider subject.

The frontend removes callback parameters using `history.replaceState` after parsing them.

- `create-ballot` success restores session and opens the pending ballot form.
- Direct social sign-in restores Voter ID without opening create flow.
- Link success refreshes profile and exposes the linked provider.
- Cancel/failure displays safe copy and does not clear an already-valid Vote System session.

## Rate limits

Public social start endpoints are protected by a separate Redis sliding-window policy:

```text
20 requests / minute / IP
```

The value is configurable through `RATE_LIMIT_SOCIAL_START_LIMIT` and `RATE_LIMIT_SOCIAL_START_WINDOW`. Authenticated link-start requests also pass through the normal security and refresh/retry lifecycle.

## Verification boundaries

Automated tests verify:

- OAuth state and Google OIDC nonce generation;
- invalid/mismatched state failure;
- durable provider-subject uniqueness;
- private/missing GitHub email;
- explicit verified-email linking;
- provider-subject collision rejection;
- normal Vote System refresh-cookie issuance;
- provider-token repository is no-op;
- JWT `SecurityContext` is not stored in HTTP session;
- provider discovery and disabled-provider fallback;
- safe callback parsing and create-ballot continuation;
- provider cancellation without clearing an existing session;
- responsive, keyboard, focus, and touch-target behavior.

CI intentionally does not contain production provider secrets and does not call live provider token endpoints. After changing provider credentials, scopes, redirect URLs, cookie policy, DNS, or backend host, run one manual end-to-end login and link smoke per provider.