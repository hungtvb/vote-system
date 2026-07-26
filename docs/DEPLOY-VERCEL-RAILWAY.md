# Deploy frontend to Vercel and backend to Railway

## Target architecture

- Vercel: `frontend/` Next.js static export
- Railway: Spring Boot API from `Dockerfile.railway`
- PostgreSQL: existing Supabase or a Railway PostgreSQL service
- Redis: existing managed Redis or a Railway Redis service

## 1. Railway backend

Create a Railway project from `hungtvb/vote-system` and deploy the repository root. Railway reads `railway.toml` and builds `Dockerfile.railway`.

Required variables:

```text
DB_URL=jdbc:postgresql://<host>:<port>/<database>?sslmode=require
DB_USERNAME=<username>
DB_PASSWORD=<password>
REDIS_URL=<redis-url>
JWT_SECRET=<at-least-32-random-characters>
JWT_ISSUER=vote-system
CORS_ALLOWED_ORIGINS=https://<vercel-production-domain>
REFRESH_COOKIE_SECURE=true
REFRESH_COOKIE_SAME_SITE=None
REDIS_HEALTH_ENABLED=true
```

Keep the remaining defaults from `.env.example`, or override them in Railway when needed.

Health check:

```text
GET /actuator/health
```

After Railway assigns a public domain, record the HTTPS origin, for example:

```text
https://vote-system-api.up.railway.app
```

## 2. Vercel frontend

Import the same GitHub repository into Vercel with:

```text
Root Directory: frontend
Framework Preset: Next.js
Install Command: npm ci
Build Command: npm run build
Output Directory: out
```

Set this environment variable for Production and Preview:

```text
NEXT_PUBLIC_API_BASE_URL=https://<railway-backend-domain>
```

The frontend API transport already sends `credentials: include`, so auth refresh cookies require the Railway settings shown above.

## 3. Update Railway CORS after Vercel creates the domain

Set `CORS_ALLOWED_ORIGINS` to the exact Vercel production origin. For multiple explicit origins, use the format supported by the application's CORS configuration. Do not use `*` with credentialed requests.

Preview deployments use different domains. Either add only selected stable preview origins or use a dedicated staging frontend/backend pair. Avoid allowing arbitrary `*.vercel.app` origins in production.

## 4. Verification

Backend:

```text
GET https://<railway-domain>/actuator/health
GET https://<railway-domain>/v3/api-docs
```

Frontend:

- page loads from Vercel
- registration and login succeed
- refresh cookie is stored with `Secure` and `SameSite=None`
- create, search, vote, close and delete flows work
- SSE vote updates connect to the Railway API
- logout and logout-all revoke sessions

## Cookie risk

Vercel and Railway domains are cross-site. Some browsers or privacy configurations may block third-party cookies. The robust production setup is to use custom subdomains under one registrable domain, for example:

```text
app.example.com -> Vercel
api.example.com -> Railway
```

Then configure:

```text
NEXT_PUBLIC_API_BASE_URL=https://api.example.com
CORS_ALLOWED_ORIGINS=https://app.example.com
REFRESH_COOKIE_SECURE=true
REFRESH_COOKIE_SAME_SITE=None
```

A same-origin reverse proxy is another option, but it must be verified carefully for SSE streaming and `Set-Cookie` behavior.
