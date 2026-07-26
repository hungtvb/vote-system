# Google and GitHub social login

Vote System supports optional Google OpenID Connect and GitHub OAuth2 login while preserving the existing application session model:

- the provider authorization-code exchange is handled only by Spring Security;
- provider access tokens are never returned to frontend JavaScript;
- provider access and refresh tokens are not persisted;
- a successful callback creates the normal Vote System JWT access token and `HttpOnly` refresh cookie;
- frontend startup restores the application session through `POST /api/v1/auth/refresh` and `GET /api/v1/users/me`.

Email/password registration and login remain available when no social provider is configured.

## Provider configuration

```env
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
GITHUB_CLIENT_ID=
GITHUB_CLIENT_SECRET=
SOCIAL_LOGIN_SUCCESS_URL=https://app.example.com/
SOCIAL_LOGIN_FAILURE_URL=https://app.example.com/
```

Provider registrations are enabled only when both the client ID and client secret are present. The public discovery endpoint returns only enabled providers:

```http
GET /api/v1/auth/social/providers
```

```json
{
  "providers": ["google", "github"]
}
```

An installation with no provider secrets returns an empty list and continues to support email/password authentication. Starting a disabled provider returns `404`.

## Provider callback URLs

Register the exact backend callback URLs in the provider consoles:

```text
https://api.example.com/login/oauth2/code/google
https://api.example.com/login/oauth2/code/github
```

For same-origin production, replace `api.example.com` with the application host. For local backend development the defaults are:

```text
http://localhost:8080/login/oauth2/code/google
http://localhost:8080/login/oauth2/code/github
```

Do not configure wildcard callback URLs.

## Scopes

Google uses OpenID Connect with:

```text
openid profile email
```

Spring Security stores the authorization request in the temporary HTTP session and validates OAuth `state`. The OIDC request includes a nonce and the OIDC provider response is validated by the framework.

GitHub uses:

```text
read:user
```

Vote System does not require `user:email`. A GitHub account with a private or missing email is represented by its durable GitHub subject ID and receives a privacy-safe local display name. The provider email is stored only when it is returned; it is never treated as verified for automatic ownership decisions.

## Browser entry points

The frontend first loads configured providers, then starts a selected flow:

```http
POST /api/v1/auth/social/google/start
Content-Type: application/json

{"intent":"authenticate"}
```

or:

```json
{"intent":"create-ballot"}
```

The response contains a backend-generated authorization URL. The browser navigates to that URL; callers cannot provide an arbitrary return URL.

For an authenticated account link:

```http
POST /api/v1/auth/social/github/link/start
Authorization: Bearer <access-token>
```

The authenticated user ID is stored in the temporary OAuth session before redirecting to the provider.

## Temporary OAuth session

The JWT API remains the application authentication mechanism. An HTTP session exists only during the authorization-code round trip:

```env
OAUTH_SESSION_TIMEOUT=10m
OAUTH_SESSION_COOKIE_NAME=vote_oauth
OAUTH_SESSION_COOKIE_SECURE=true
OAUTH_SESSION_COOKIE_SAME_SITE=Lax
```

The session stores the Spring Security authorization request and the allowlisted Vote System intent. It is invalidated immediately after success or failure. A separate Vote System refresh cookie is issued after a successful social login or link.

For a cross-site HTTPS deployment, both the temporary OAuth cookie and the Vote System refresh cookie may require `SameSite=None` and `Secure=true`:

```env
OAUTH_SESSION_COOKIE_SECURE=true
OAUTH_SESSION_COOKIE_SAME_SITE=None
REFRESH_COOKIE_SECURE=true
REFRESH_COOKIE_SAME_SITE=None
```

Use this only with HTTPS and explicit CORS origins.

## Local identity model

Flyway V6 adds `user_identities` with two uniqueness guarantees:

- `(provider, provider_subject)` identifies exactly one local user;
- `(user_id, provider)` prevents one local user from linking multiple identities from the same provider.

A social-only local user may have no email and no password hash. The public and private profile APIs therefore model email as optional. The Voter ID displays `EMAIL NOT SHARED` when the provider did not supply a usable email.

Repeated login with the same provider subject always resolves the same local user and does not create duplicate ballots, votes, roles, or profile ownership.

## Safe account linking

Vote System does not silently merge accounts by email.

- Existing provider subject: resolve the already-linked local user.
- New provider subject with no matching verified email: create a new local user.
- Google verified email matching an existing local account: redirect with `account_link_required`.
- The user must sign in to the existing account and choose `LINK GOOGLE` or `LINK GITHUB` from Voter ID.
- A provider subject already linked to another local user cannot be moved through login.
- A second identity for the same provider cannot be linked to one local user.
- GitHub public email is not considered verified for automatic linking.

No unlink endpoint is exposed in this release, so a user cannot accidentally remove their last usable login method.

## Callback results

Success and failure redirects use fixed configured base URLs. Query parameters contain only allowlisted status values and codes:

```text
/?social=success&provider=google&intent=create-ballot
/?social=linked&provider=github&intent=link-account
/?social=error&code=access_denied&intent=authenticate
```

The URL never contains the application access token, refresh token, provider token, authorization code, email, or provider subject.

The frontend removes the callback parameters with `history.replaceState` after parsing them.

- `create-ballot` success restores the Vote System session and opens the pending ballot form.
- Direct social sign-in restores Voter ID without opening a ballot form.
- Link success refreshes Voter ID and shows the linked provider.
- Cancel or failure shows actionable copy and does not clear an already-valid Vote System session.

## Verification boundaries

Automated tests use deterministic fake client registrations and provider principals. They verify state/nonce generation, invalid-state failure, subject uniqueness, private GitHub email, explicit verified-email linking, session issuance, refresh-cookie output, callback continuation, provider discovery, and responsive provider controls.

A real-provider release still requires manual smoke tests with registered Google and GitHub applications because CI does not possess production provider credentials and does not call external provider token endpoints.
