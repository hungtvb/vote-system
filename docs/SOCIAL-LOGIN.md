# Google and GitHub social login

Vote System supports optional Google OpenID Connect and GitHub OAuth2 login while preserving the normal application session model:

- provider authorization-code exchange is handled only by Spring Security;
- provider access tokens never enter frontend JavaScript;
- provider access and refresh tokens are not persisted;
- a successful callback creates the normal Vote System refresh session;
- frontend callback continuation restores session and private profile through one `POST /api/v1/auth/refresh` request;
- email/password registration and login remain available when no provider is configured.

## Active production configuration

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

Exact callbacks:

```text
https://api.ballotbox.io.vn/login/oauth2/code/google
https://api.ballotbox.io.vn/login/oauth2/code/github
```

Do not configure wildcard callbacks. A provider is enabled only when both client ID and client secret are present.

## Provider discovery

```http
GET /api/v1/auth/social/providers
```

```json
{
  "providers": ["google", "github"]
}
```

No configured secrets returns an empty list and leaves email/password auth usable. Starting a disabled provider returns `404`.

## Local callback URLs

```text
http://localhost:8080/login/oauth2/code/google
http://localhost:8080/login/oauth2/code/github
```

Local settings:

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

Google OpenID Connect scopes:

```text
openid profile email
```

Spring Security stores the authorization request in the temporary OAuth session, validates `state`, adds OIDC nonce, and validates the provider response.

GitHub scope:

```text
read:user
```

Vote System does not require `user:email`. Private or missing GitHub email is supported because the durable provider subject identifies the local user. Provider email is stored only when returned and is not treated as verified for automatic account ownership.

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

The local authenticated user is captured in temporary OAuth context before redirecting to the provider.

## Temporary OAuth session

JWT bearer authentication remains the application session mechanism. An HTTP session exists only during the authorization-code round trip.

```env
OAUTH_SESSION_TIMEOUT=10m
OAUTH_SESSION_COOKIE_NAME=vote_oauth
OAUTH_SESSION_COOKIE_SECURE=true
OAUTH_SESSION_COOKIE_SAME_SITE=Lax
```

The temporary session contains only Spring Security authorization-request state and allowlisted Vote System intent. It is invalidated after success or failure.

A separate Vote System refresh cookie is issued after successful social login or link. The JWT `SecurityContext` is request-scoped and is not persisted in the HTTP session.

A future cross-site deployment on different registrable domains may require both cookies to use `SameSite=None` with `Secure=true`. The current `app.ballotbox.io.vn` / `api.ballotbox.io.vn` topology is same-site and uses `Lax`.

## Local identity model

Flyway V6 adds `user_identities` with uniqueness guarantees:

- `(provider, provider_subject)` identifies one local user;
- `(user_id, provider)` prevents one local user from linking multiple identities for the same provider.

A social-only local user may have no email and no password hash. Private profile APIs model email as optional. Voter ID displays a safe fallback when email is not shared.

Repeated login with the same provider subject resolves the same local user and does not duplicate ballots, votes, role, or profile ownership.

## Safe account linking

Vote System never silently merges accounts by email.

- Existing provider subject: resolve its linked local user.
- New subject without a verified local-account collision: create a new local user.
- Google verified email matching an existing local account: return `account_link_required`.
- The user signs into the existing account and explicitly chooses `LINK GOOGLE` or `LINK GITHUB`.
- A provider subject already linked to another user cannot be moved through login.
- A second identity for the same provider cannot be linked to one user.
- GitHub public email is not trusted as verified for automatic linking.

No unlink endpoint exists in the current release, preventing accidental removal of the last usable login method.

## Callback results

Success and failure redirect to fixed configured frontend URLs with allowlisted metadata only:

```text
/?social=success&provider=google&intent=create-ballot
/?social=linked&provider=github&intent=link-account
/?social=error&code=access_denied&intent=authenticate
```

The URL never contains application tokens, provider tokens, authorization code, email, or provider subject. The frontend removes callback parameters with `history.replaceState` after parsing.

- `create-ballot` success restores session/profile through the refresh bootstrap and opens the pending form.
- Direct social sign-in restores authenticated Voter ID.
- Link success restores the bootstrap profile with updated linked-provider state.
- Cancel/failure displays safe catalog copy and does not clear an already-valid Vote System session.

The normal callback path does not call `GET /api/v1/users/me`. That endpoint remains only for explicit profile retrieval and a temporary older-backend compatibility fallback.

## Rate limits

Public social start endpoints use a separate Redis sliding-window policy:

```text
20 requests / minute / IP
```

Configure with `RATE_LIMIT_SOCIAL_START_LIMIT` and `RATE_LIMIT_SOCIAL_START_WINDOW`. Authenticated link-start requests also use the normal security and refresh/retry lifecycle.

## Verification boundaries

Automated tests verify:

- OAuth state and Google OIDC nonce generation;
- invalid or mismatched state failure;
- durable provider-subject uniqueness;
- private/missing GitHub email;
- explicit verified-email linking;
- provider-subject collision rejection;
- Vote System refresh-cookie issuance;
- provider-token repository is no-op;
- JWT `SecurityContext` is not stored in HTTP session;
- provider discovery and disabled-provider fallback;
- safe callback parsing and create-ballot continuation;
- provider cancellation without clearing an existing session;
- single-request session/profile bootstrap after callback;
- responsive, keyboard, focus, and touch-target behavior.

CI intentionally has no production provider secrets and does not call live provider token endpoints. After changing credentials, scopes, redirects, cookie policy, DNS, or backend host, run one manual login and link smoke per provider.
