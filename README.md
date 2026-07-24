# Vote System

A production-oriented vote platform built as a **modular monolith**. Domain boundaries keep auth, posts, and votes easy to extract into separate services later without rewriting the whole application.

## Stack

### Backend

- Java 21
- Spring Boot 3.5.14
- Spring Security + signed JWT bearer tokens
- Spring Data JPA
- Spring Data Redis + Spring Cache
- PostgreSQL 17
- Redis 7.4
- Flyway
- Testcontainers

### Frontend

- React 19 + TypeScript
- Vite
- Responsive custom CSS
- Optimistic voting with server reconciliation and rollback

## Current capabilities

- Register and login with short-lived access tokens
- Rotating refresh tokens in `HttpOnly`, path-scoped cookies
- Refresh-token hashes stored in PostgreSQL; raw tokens are never persisted
- Single-session logout, logout-all, expiry handling, and refresh-token replay detection
- BCrypt password hashing and stateless JWT API authentication
- Create, browse, search, paginate, edit, and delete posts
- Ownership enforcement: only the author can update or delete a post
- Upvote, downvote, change vote, and remove vote
- Current user's vote returned with each post without N+1 queries
- One vote per user/post enforced by a database constraint
- Atomic post score updates
- Deleting a post removes its votes in the same transaction
- Redis connection, JSON serialization, shared cache manager, and Actuator health integration
- Loading, empty, error, ownership, and responsive UI states
- PostgreSQL and Redis integration tests through Testcontainers
- Backend, frontend, and production-container CI builds

## Run locally

Requirements: Java 21, Maven 3.6.3+, Node.js 22+, npm, and Docker.

Start PostgreSQL, Redis, and the API:

```bash
docker compose up -d postgres redis
mvn spring-boot:run
```

In another terminal, start the frontend:

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. Vite proxies `/api` and `/actuator` to the Spring Boot server at `http://localhost:8080`.

Health check:

```bash
curl http://localhost:8080/actuator/health
```

With health details enabled, both `db` and `redis` should report `UP`.

## Redis foundation

Redis is configured as shared infrastructure but is not yet on the critical business path. The application exposes:

- `RedisTemplate<String, Object>` with string keys and JSON values
- a transaction-aware `RedisCacheManager`
- a default cache TTL of 30 seconds
- connection and command timeouts of 2 seconds
- Redis Actuator health checks

Local configuration uses `redis://localhost:6379`. Override it with `REDIS_URL`. Render keeps the Redis health indicator disabled until a managed Redis instance is attached, so the existing deployment does not fail before rate limiting and ranking start depending on Redis.

## Authentication lifecycle

The access token is a signed JWT with a default lifetime of 15 minutes. The refresh token is an opaque random value stored only in an `HttpOnly` cookie. PostgreSQL stores its SHA-256 hash and session metadata.

Every successful refresh rotates the token. Reusing a rotated token is treated as possible token theft and revokes every active refresh session for that user. Revoking a refresh session does not perform a database lookup for every API request; an already-issued access token remains valid until its short expiry.

Register and save the refresh cookie:

```bash
curl -s http://localhost:8080/api/v1/auth/register \
  -c cookies.txt \
  -H 'Content-Type: application/json' \
  -d '{"email":"hung@example.com","password":"strong-password"}'
```

Use the returned `accessToken`:

```bash
export TOKEN='<accessToken>'
```

Rotate the refresh token and receive a new access token:

```bash
curl -s http://localhost:8080/api/v1/auth/refresh \
  -X POST \
  -b cookies.txt \
  -c cookies.txt
```

Revoke the current refresh session and clear its cookie:

```bash
curl -i http://localhost:8080/api/v1/auth/logout \
  -X POST \
  -b cookies.txt \
  -c cookies.txt
```

Revoke every refresh session belonging to the current user:

```bash
curl -i http://localhost:8080/api/v1/auth/logout-all \
  -X POST \
  -H "Authorization: Bearer $TOKEN"
```

## Post and vote API walkthrough

Create a post:

```bash
curl -s http://localhost:8080/api/v1/posts \
  -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"title":"First post","content":"Production vote system"}'
```

Update an owned post:

```bash
curl -s http://localhost:8080/api/v1/posts/<postId> \
  -X PUT \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"title":"Updated title","content":"Updated content"}'
```

Delete an owned post:

```bash
curl -i http://localhost:8080/api/v1/posts/<postId> \
  -X DELETE \
  -H "Authorization: Bearer $TOKEN"
```

Vote:

```bash
curl -s http://localhost:8080/api/v1/posts/<postId>/vote \
  -X PUT \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"type":"UP"}'
```

Remove vote:

```bash
curl -s http://localhost:8080/api/v1/posts/<postId>/vote \
  -X DELETE \
  -H "Authorization: Bearer $TOKEN"
```

## Deploy with Supabase and Render

The production image builds the React application first, copies it into Spring Boot's static resources, and serves the UI and API from one Render web service. This avoids cross-origin configuration and keeps the first deployment inexpensive.

The current integration uses Supabase as managed PostgreSQL. Authentication, refresh sessions, and JWT issuance belong to the Spring Boot application; Supabase Auth and Realtime are not enabled for this version.

1. Open the Supabase `vote-system` project (`kapfoxuuuprmuersmoqf`) and choose **Connect**.
2. Copy the **Session pooler** connection details and configure these Render secrets:

```text
DB_URL=jdbc:postgresql://<pooler-host>:5432/postgres?sslmode=require
DB_USERNAME=<pooler-username>
DB_PASSWORD=<database-password>
```

3. Create a Render Blueprint from `render.yaml`.
4. Supply the database secrets when Render prompts for values. `JWT_SECRET` is generated by Render and `REFRESH_COOKIE_SECURE=true` is configured by the Blueprint.
5. On startup, Flyway creates or upgrades the application tables. Render verifies the deployment through `/actuator/health`.

When a managed Redis instance is attached, set `REDIS_URL` and switch `REDIS_HEALTH_ENABLED=true`.

Do not commit the Supabase database password, raw refresh tokens, Redis credentials, or complete connection strings to the repository.

## Why one database now?

The first version uses one PostgreSQL database because it keeps transactions, development, and deployment simple. Domain packages own their tables and avoid leaking repositories into other controllers. When a domain is extracted, its tables can be migrated to a service-owned database and communication can move to APIs or events.

## Next milestones

1. Sliding-window Redis rate limiting
2. Redis hot ranking
3. Outbox events
4. WebSocket/SSE fan-out where realtime delivery is justified
5. Metrics dashboards and load tests
