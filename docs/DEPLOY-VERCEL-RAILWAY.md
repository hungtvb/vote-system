# Deploy frontend to Vercel and backend to Railway

## Production architecture

- Frontend: Vercel, built from `frontend/`
- Backend: Railway, built from `Dockerfile.railway`
- PostgreSQL: Supabase Session Pooler
- Redis: managed Redis such as Upstash
- DNS: Cloudflare

Production domains:

```text
https://app.ballotbox.io.vn -> Vercel
https://api.ballotbox.io.vn -> Railway
```

Both hosts are HTTPS subdomains of `ballotbox.io.vn`, so browser requests are cross-origin but same-site.

## 1. Railway backend

Connect `hungtvb/vote-system` to Railway and deploy the repository root. Railway reads `railway.toml` and builds `Dockerfile.railway`.

Required variables:

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

Do not set `PORT`; Railway provides it and Spring Boot reads the platform value.

Health and API documentation:

```text
GET https://api.ballotbox.io.vn/actuator/health
GET https://api.ballotbox.io.vn/v3/api-docs
GET https://api.ballotbox.io.vn/swagger-ui.html
```

## 2. Vercel frontend

Import the same GitHub repository with:

```text
Root Directory: frontend
Framework Preset: Next.js
Install Command: npm ci
Build Command: npm run build
Output Directory: .next
```

The app uses Next.js `output: "export"`, but Vercel's Next.js builder still needs the framework build directory and routing manifests. The repository therefore overrides any project-level `out` setting with `outputDirectory: ".next"` in `frontend/vercel.json`. Using `out` causes `NEXT_NO_ROUTES_MANIFEST`.

Production environment variable:

```text
NEXT_PUBLIC_API_BASE_URL=https://api.ballotbox.io.vn
```

`NEXT_PUBLIC_API_BASE_URL` is embedded at build time, so redeploy after changing it.

The frontend API transport sends credentialed requests. Do not use `*` for backend CORS when credentials are enabled.

## 3. DNS and TLS

Cloudflare DNS records for `app` and `api` should initially remain `DNS only` while Vercel and Railway validate the domains and issue certificates.

Always test the frontend through:

```text
https://app.ballotbox.io.vn
```

Do not test production through `http://app.ballotbox.io.vn`; HTTP and HTTPS are different CORS origins, and secure refresh cookies are not sent over HTTP.

## 4. Verification

Backend:

- `/actuator/health` returns `UP`
- `/v3/api-docs` is reachable
- Flyway completes against Supabase
- Redis health is `UP`

Frontend and authentication:

- page loads from `https://app.ballotbox.io.vn`
- registration and login succeed
- refresh cookie is stored with `Secure` and `SameSite=Lax`
- refreshing the page restores the session
- create, search, vote, close and delete flows work
- SSE vote updates connect to `https://api.ballotbox.io.vn`
- logout and logout-all revoke sessions

## 5. Preview deployments

Vercel preview deployments use separate origins. Production should not allow arbitrary `*.vercel.app` origins.

Use either:

- a dedicated staging frontend/backend pair, or
- an explicit allow-list containing only selected stable preview origins.
