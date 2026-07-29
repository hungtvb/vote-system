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

Both hosts are HTTPS subdomains of `ballotbox.io.vn`. Requests are cross-origin but same-site, so `SameSite=Lax` is appropriate for the current refresh/OAuth cookies.

Render is not an active production target. The root `Dockerfile` remains for combined-image CI/runtime smoke and optional single-service deployment.

## 1. Railway backend

Connect `hungtvb/vote-system` to Railway and deploy the repository root. Railway reads `railway.toml` and builds `Dockerfile.railway`.

`Dockerfile.railway` sets:

```text
SPRING_PROFILES_ACTIVE=production
```

Do not override this unless a deliberate staging profile is introduced. The production profile disables springdoc, sets Spring/Hibernate logging to INFO, and disables Hibernate statistics/session-event metrics.

### Required variables

```text
DB_URL=jdbc:postgresql://<supabase-session-pooler-host>:5432/postgres?sslmode=require
DB_USERNAME=postgres.<supabase-project-ref>
DB_PASSWORD=<supabase-database-password>
REDIS_URL=rediss://default:<password>@<host>:<port>
JWT_SECRET=<at-least-32-random-characters>
JWT_ISSUER=vote-system
CORS_ALLOWED_ORIGINS=https://app.ballotbox.io.vn
REFRESH_COOKIE_SECURE=true
REFRESH_COOKIE_SAME_SITE=Lax
REDIS_HEALTH_ENABLED=true
```

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
OAUTH_SESSION_COOKIE_NAME=vote_oauth
OAUTH_SESSION_COOKIE_SECURE=true
OAUTH_SESSION_COOKIE_SAME_SITE=Lax
```

Provider callback URLs:

```text
https://api.ballotbox.io.vn/login/oauth2/code/google
https://api.ballotbox.io.vn/login/oauth2/code/github
```

Do not configure wildcard callbacks. Do not store provider access/refresh tokens in application configuration or persistence.

Detailed provider rules: [`SOCIAL-LOGIN.md`](SOCIAL-LOGIN.md).

### Health and production API-doc policy

Expected public endpoint:

```http
GET https://api.ballotbox.io.vn/actuator/health
```

Expected unavailable production routes:

```text
https://api.ballotbox.io.vn/v3/api-docs
https://api.ballotbox.io.vn/v3/api-docs/swagger-config
https://api.ballotbox.io.vn/swagger-ui.html
https://api.ballotbox.io.vn/swagger-ui/index.html
```

Spring Security permits the documentation paths when springdoc is active, but `application-production.yml` disables the springdoc beans. Do not treat a public Swagger UI as normal production behavior.

Only health is public under Actuator security. `info` and `metrics` require authentication.

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

Local example: [`../frontend/.env.example`](../frontend/.env.example).

## 3. DNS and TLS

Cloudflare records for `app` and `api` should remain `DNS only` while Vercel/Railway validate domains and issue certificates. Enable proxying later only after validating SSE and cache behavior.

Always test through:

```text
https://app.ballotbox.io.vn
```

Do not validate production through HTTP. HTTP and HTTPS are distinct CORS origins, and secure refresh/OAuth cookies are not sent over HTTP.

## 4. Deployment verification

### Backend startup and hardening

- Railway builds `Dockerfile.railway`.
- Runtime reports the `production` Spring profile.
- Flyway completes against Supabase.
- Hibernate validation succeeds.
- `/actuator/health` returns `UP`.
- PostgreSQL and Redis health components report `UP`.
- `/v3/api-docs` and Swagger UI are unavailable.
- Startup/request logs do not contain springdoc enablement warnings, Hibernate `Session Metrics`, or broad Spring/Hibernate DEBUG traffic.

### Frontend and core product

- `https://app.ballotbox.io.vn` returns Ballot Edition with CSS/JS/assets loaded.
- Public LATEST/HOT/TOP starts before session restore completes.
- Registration and email/password login succeed.
- Refresh cookie is `HttpOnly`, `Secure`, and `SameSite=Lax`.
- A hard refresh restores session/profile through one `POST /api/v1/auth/refresh` request.
- The normal bootstrap path does not call `GET /api/v1/users/me` afterward.
- Create, search, filter, vote, close, edit, and delete work.
- Profile edit/public profile/Ballot Mark render correctly.
- Empty public bio shows no default user-authored sentence.
- VI/EN switching works and survives refresh through local preference.
- `MINE` is unavailable to guests and returns only the authenticated author's ballots.
- Optimistic voting reconciles authoritative REST counts.
- SSE converges across two tabs observing the same open ballot.
- Logout and logout-all revoke refresh sessions.

The authenticated profile's `preferredLocale` is currently durable but not automatically applied during bootstrap; TON-191 tracks that known gap.

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

Use Railway logs and authenticated Actuator metrics to separate ordinary API latency from long-lived SSE connections.

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

Logs and metric tags must never include user IDs, ballot IDs, email, cookies, provider tokens, refresh-token hashes, or raw tokens.

## 6. Preview deployments

Vercel previews use separate origins. Production must not allow arbitrary `*.vercel.app` origins.

Use either:

- a dedicated staging frontend/backend pair; or
- an explicit CORS allow-list containing only selected stable preview origins.

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
