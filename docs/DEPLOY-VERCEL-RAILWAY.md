# Deploy frontend to Vercel and backend to Railway

This is the active production topology for Vote System.

## Production architecture

- Frontend: Vercel, built from `frontend/`.
- Backend: Railway, built from repository root with `Dockerfile.railway`.
- PostgreSQL: Supabase Session Pooler.
- Redis: managed Redis.
- DNS/TLS: Cloudflare plus Vercel/Railway certificates.

Production domains:

```text
https://app.ballotbox.io.vn -> Vercel
https://api.ballotbox.io.vn -> Railway
```

Both hosts are HTTPS subdomains of `ballotbox.io.vn`. Requests are cross-origin but same-site. Refresh cookies use `SameSite=Strict`; the temporary OAuth session uses `SameSite=Lax` because the provider callback is a top-level cross-site navigation.

Render is not an active production target. The root `Dockerfile` remains for combined-image CI/runtime smoke and optional single-service deployment.

## 1. Railway backend

Connect `hungtvb/vote-system` to Railway and deploy the repository root. Railway reads `railway.toml` and builds `Dockerfile.railway`.

`Dockerfile.railway` sets:

```text
SPRING_PROFILES_ACTIVE=production
```

Do not override this unless a deliberate staging profile is introduced. The production profile disables springdoc, reduces Spring/Hibernate logging, disables Hibernate statistics/session-event metrics, and rejects unsafe security configuration at startup.

### Required variables

```text
DB_URL=jdbc:postgresql://<supabase-session-pooler-host>:5432/postgres?sslmode=require
DB_USERNAME=postgres.<supabase-project-ref>
DB_PASSWORD=<supabase-database-password>
REDIS_URL=rediss://default:<password>@<host>:<port>
JWT_SECRET=<cryptographically-random-secret-of-at-least-32-bytes>
JWT_ISSUER=vote-system
CORS_ALLOWED_ORIGINS=https://app.ballotbox.io.vn
REFRESH_COOKIE_NAME=__Secure-vote_refresh
OAUTH_SESSION_COOKIE_NAME=__Secure-vote_oauth
REDIS_HEALTH_ENABLED=true
```

The production profile enforces `Secure` cookies and these SameSite policies:

```text
refresh cookie       Strict
OAuth session cookie Lax
```

Startup fails when JWT secret, CORS origin, or cookie settings are missing or unsafe. Placeholders, wildcard origins, non-HTTPS origins, paths, queries, embedded credentials, localhost/loopback origins, non-Secure cookies, and names without `__Secure-` are rejected.

Do not set `PORT`; Railway supplies it and Spring Boot reads `${PORT}`.

### Recommended runtime variables

```text
ACCESS_TOKEN_TTL=PT15M
REFRESH_TOKEN_TTL=P30D
REDIS_CONNECT_TIMEOUT=2s
REDIS_COMMAND_TIMEOUT=2s
CACHE_DEFAULT_TTL=PT30S
VOTE_STREAM_HEARTBEAT_MS=15000
VOTE_STREAM_TIMEOUT_MS=1800000
VOTE_SIDE_EFFECT_QUEUE_CAPACITY=200
VOTE_SIDE_EFFECT_SHUTDOWN_WAIT_SECONDS=10
RATE_LIMIT_ENABLED=true
RATE_LIMIT_FAIL_OPEN=true
RATE_LIMIT_PROFILE_UPDATE_LIMIT=10
RATE_LIMIT_PROFILE_UPDATE_WINDOW=PT1H
```

`RATE_LIMIT_FAIL_OPEN=true` applies only to non-authentication operations. Login, registration, refresh, social authentication, and social linking always fail closed when Redis cannot verify their limit and return `503 RATE_LIMIT_UNAVAILABLE`.

The vote side-effect queue is bounded. After commit, ranking and SSE tasks enter separate ordered executors. Queue saturation may block enqueue briefly; ordinary Redis/SSE network I/O does not remain on the vote request thread.

### Optional social-login variables

Configure only enabled providers:

```text
GOOGLE_CLIENT_ID=<google-client-id>
GOOGLE_CLIENT_SECRET=<google-client-secret>
GITHUB_CLIENT_ID=<github-client-id>
GITHUB_CLIENT_SECRET=<github-client-secret>
SOCIAL_LOGIN_SUCCESS_URL=https://app.ballotbox.io.vn/
SOCIAL_LOGIN_FAILURE_URL=https://app.ballotbox.io.vn/
OAUTH_SESSION_TIMEOUT=10m
OAUTH_SESSION_COOKIE_NAME=__Secure-vote_oauth
```

Provider callback URLs:

```text
https://api.ballotbox.io.vn/login/oauth2/code/google
https://api.ballotbox.io.vn/login/oauth2/code/github
```

Do not configure wildcard callbacks. Do not store provider access/refresh tokens in application configuration or persistence.

Detailed provider rules: [`SOCIAL-LOGIN.md`](SOCIAL-LOGIN.md).

### Health, Actuator, and production API-doc policy

Expected public endpoint:

```http
GET https://api.ballotbox.io.vn/actuator/health
```

Administrator-only boundary:

```text
/actuator/** except /actuator/health/**
```

This includes `/actuator`, `info`, `metrics`, metric details, and any endpoint exposed later.

Expected unavailable production routes:

```text
https://api.ballotbox.io.vn/v3/api-docs
https://api.ballotbox.io.vn/v3/api-docs/swagger-config
https://api.ballotbox.io.vn/swagger-ui.html
https://api.ballotbox.io.vn/swagger-ui/index.html
```

Spring Security permits the documentation paths when springdoc is active, but `application-production.yml` disables the springdoc beans. Do not treat a public Swagger UI as normal production behavior.

## 2. Vercel frontend

Import the same repository with:

```text
Root Directory: frontend
Framework Preset: Next.js
Install Command: npm ci
Build Command: npm run build
Output Directory: .next
```

The application uses Next.js `output: "export"`, but Vercel's Next.js builder still needs the framework build directory and route manifests. `frontend/vercel.json` sets `outputDirectory: ".next"`; using `out` causes `NEXT_NO_ROUTES_MANIFEST`.

Production variable:

```text
NEXT_PUBLIC_API_BASE_URL=https://api.ballotbox.io.vn
```

`NEXT_PUBLIC_API_BASE_URL` is embedded at build time. Redeploy after changing it.

The frontend transport uses `credentials: include`. Backend CORS must return the explicit frontend origin and `Access-Control-Allow-Credentials: true`; do not use `*` with cookies.

`frontend/vercel.json` installs CSP, HSTS, frame denial, `nosniff`, referrer policy, permissions policy, COOP, and an admin no-index header. The CSP explicitly permits only the production API host. A staging deployment must use its own reviewed header configuration rather than widening production to arbitrary preview origins.

Local example: [`../frontend/.env.example`](../frontend/.env.example).

## 3. DNS and TLS

Cloudflare records for `app` and `api` should remain `DNS only` while Vercel/Railway validate domains and issue certificates. Enable proxying later only after validating SSE and cache behavior.

Always test through:

```text
https://app.ballotbox.io.vn
```

Do not validate production through HTTP. HTTP and HTTPS are distinct CORS origins, and Secure refresh/OAuth cookies are not sent over HTTP.

## 4. Deployment verification

### Backend startup and hardening

- Railway builds `Dockerfile.railway`.
- Runtime reports the `production` Spring profile.
- Flyway completes through V14 or later against Supabase.
- Hibernate validation succeeds.
- `/actuator/health` returns `UP`.
- PostgreSQL and Redis health components report `UP`.
- `/v3/api-docs` and Swagger UI are unavailable.
- anonymous and USER tokens cannot read `/actuator`, `/actuator/info`, or `/actuator/metrics`.
- startup/request logs do not contain springdoc enablement warnings, Hibernate `Session Metrics`, or broad Spring/Hibernate DEBUG traffic.
- removing or replacing `JWT_SECRET` with a committed placeholder makes startup fail.

### Authentication security

- Refresh cookie name is `__Secure-vote_refresh` and attributes include `HttpOnly`, `Secure`, `Path=/api/v1/auth`, and `SameSite=Strict`.
- OAuth session cookie name is `__Secure-vote_oauth` and attributes include `HttpOnly`, `Secure`, and `SameSite=Lax`.
- A hard refresh restores session/profile through one `POST /api/v1/auth/refresh` request.
- Login, registration, and refresh return `503 RATE_LIMIT_UNAVAILABLE` instead of bypassing abuse protection while Redis is unavailable.
- A refresh request with `Origin: https://attacker.example` remains rejected even when forged `X-Forwarded-Host/Proto` headers are supplied.
- A browser-style refresh with no Origin and `Sec-Fetch-Site: same-site` returns `403 SESSION_ORIGIN_REJECTED`.
- Logout backed by an active refresh session invalidates retained access JWTs immediately; other devices retain refresh sessions and can recover.
- Reusing an expired or already-revoked logout cookie does not rotate the token version again.
- Logout-all invalidates a retained access JWT immediately and revokes every active refresh session.
- Administrator revoke-sessions invalidates retained access JWTs even when the revoked refresh-session count is zero.
- Suspend, ban, explicit restore, and role promotion invalidate tokens issued before the state change.

### Frontend and core product

- `https://app.ballotbox.io.vn` returns Ballot Edition with CSS/JS/assets loaded.
- Vercel returns CSP, HSTS, frame, referrer, permissions, content-type, and COOP headers.
- Public LATEST/HOT/TOP starts before session restore completes.
- Registration and email/password login succeed.
- Create, search, filter, vote, close, edit, and delete work.
- Profile edit/public profile/Ballot Mark render correctly.
- VI/EN switching works and survives refresh through local preference.
- `MINE` is unavailable to guests and returns only the authenticated author's ballots.
- Optimistic voting reconciles authoritative REST counts.
- SSE converges across two tabs observing the same open ballot.
- Guest and USER visits to `/admin` resolve to neutral 404 UI and do not call `/api/v1/admin/**`.
- ADMIN access to the workspace and recovery endpoints continues to work.

### CI runtime evidence

The combined-image runtime smoke activates `SPRING_PROFILES_ACTIVE=production`, uses an ephemeral random JWT secret, validates hidden Swagger routes and secure refresh-cookie attributes, and keeps raw cookie/JWT values outside uploaded artifacts. The artifact scan fails if a refresh cookie or JWT pattern is present.

### Social login

When provider credentials are configured:

- provider discovery returns only enabled providers;
- Google login uses the exact registered callback;
- GitHub login works with private/missing provider email;
- verified-email collision requires explicit linking;
- callback URLs contain only safe status metadata;
- provider cancel/failure preserves an already-valid Vote System session;
- linked provider appears in Voter ID and bootstrap profile.

CI cannot perform live provider token exchange because production secrets are absent. Run one manual login/link smoke after credential or callback changes.

## 5. Observability after deployment

Use Railway logs and ADMIN-authenticated Actuator metrics to separate ordinary API latency from long-lived SSE connections.

Important metrics:

```text
http.server.requests
vote.http.request.duration
vote.sse.connection.duration
vote.sse.subscribers.active
vote.auth.restore.total
vote.auth.restore.stage
vote.rate_limit.latency
vote.operation.total
vote.operation.stage
vote.side_effect.execution
vote.ranking.operations
vote.system.mode.requests
hikaricp.*
```

Useful completion-log fields:

```text
http_request_complete
requestId=<safe-id>
method=<HTTP method>
route=<route template>
status=<status>
durationMs=<duration>
async=<true|false>
kind=<api|stream|actuator>
outcome=<2xx|4xx|5xx|timeout|error>
```

SSE duration is recorded when the async connection completes. With low traffic, a long-lived stream can dominate platform percentiles; compare route-level metrics before changing SQL, pool size, region, or compute tier.

`vote.system.mode.requests` records only bounded result/status/code/mode/method/route-family tags for requests rejected by the operating-mode boundary. It never uses a raw request path or entity identifier as a tag.

Logs and metric tags must never include user IDs, ballot IDs, email, cookies, provider tokens, refresh-token hashes, or raw tokens.

## 6. Preview deployments

Vercel previews use separate origins. Production must not allow arbitrary `*.vercel.app` origins.

Use either:

- a dedicated staging frontend/backend pair with its own exact CORS origin and CSP API host; or
- an explicitly reviewed stable preview origin.

OAuth previews require provider callbacks registered specifically for the staging backend. Do not point production OAuth callbacks to ephemeral preview domains.

## 7. Secrets

Do not commit:

- Supabase passwords or complete production connection strings;
- Redis credentials;
- `JWT_SECRET`;
- Google/GitHub client secrets;
- raw access/refresh tokens;
- provider tokens;
- production cookie values or captured session headers.

Repository examples: [`../.env.example`](../.env.example) and [`../frontend/.env.example`](../frontend/.env.example).

See [`SECURITY-HARDENING.md`](SECURITY-HARDENING.md) for the full security boundary, kill-tests, and rollback warning.
