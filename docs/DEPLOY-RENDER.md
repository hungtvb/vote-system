# Deploy backend to Render

Render is configured as a backend-only deployment. The frontend remains on Vercel.

## Architecture

```text
https://app.ballotbox.io.vn -> Vercel
Render web service          -> Spring Boot API standby
Supabase                    -> PostgreSQL
Upstash                     -> Redis
```

`render.yaml` uses `Dockerfile.railway` because that image contains only the Spring Boot backend. The file is provider-neutral even though its current name references Railway.

## Required Render environment variables

Set these in Render Dashboard for the `hungtvb-vote-system` web service:

```text
DB_URL=jdbc:postgresql://<supabase-session-pooler-host>:5432/postgres?sslmode=require
DB_USERNAME=postgres.<supabase-project-ref>
DB_PASSWORD=<supabase-database-password>
REDIS_URL=rediss://default:<password>@<host>:<port>
JWT_SECRET=<same-production-secret-used-by-the-active-backend>
JWT_ISSUER=vote-system
CORS_ALLOWED_ORIGINS=https://app.ballotbox.io.vn
REFRESH_COOKIE_SECURE=true
REFRESH_COOKIE_SAME_SITE=Lax
REDIS_HEALTH_ENABLED=true
```

Provider environment variables are isolated. Values entered in Railway are not copied to Render.

Use the Supabase Session Pooler username exactly as shown by Supabase. For the shared pooler it is normally `postgres.<project-ref>`, not only `postgres`.

Use the same `JWT_SECRET` as the active production backend only when Render is intended as a failover for the same users and database. A different secret invalidates existing access tokens after traffic switches providers.

## Deploy verification

After saving the environment variables, trigger a manual deploy of the latest commit and verify:

```text
GET https://<render-service-domain>/actuator/health
GET https://<render-service-domain>/v3/api-docs
```

Expected health response:

```json
{"status":"UP"}
```

If startup fails, inspect the first root `Caused by:` section in the Render logs. Do not share database passwords, Redis credentials or JWT secrets.

## Traffic policy

Do not point `api.ballotbox.io.vn` at both Railway and Render with independent DNS records. Keep one active backend and one standby backend unless a real load balancer and shared session strategy are introduced.
