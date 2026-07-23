# Vote System

A production-oriented vote platform built as a **modular monolith**. Domain boundaries keep auth, posts, and votes easy to extract into separate services later without rewriting the whole application.

## Stack

### Backend

- Java 21
- Spring Boot 3.5.14
- Spring Security + signed JWT bearer tokens
- Spring Data JPA
- PostgreSQL 17
- Flyway
- Testcontainers

### Frontend

- React 19 + TypeScript
- Vite
- Responsive custom CSS
- Optimistic voting with server reconciliation and rollback

## Current capabilities

- Register, login, logout, and persisted browser session
- BCrypt password hashing and stateless JWT authentication
- Create, browse, search, and paginate posts
- Upvote, downvote, change vote, and remove vote
- Current user's vote returned with each post without N+1 queries
- One vote per user/post enforced by a database constraint
- Atomic post score updates
- Loading, empty, error, and responsive UI states
- PostgreSQL integration tests through Testcontainers
- Backend and frontend CI builds

## Run locally

Requirements: Java 21, Maven 3.6.3+, Node.js 22+, npm, and Docker.

Start PostgreSQL and the API:

```bash
docker compose up -d postgres
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

## API walkthrough

Register:

```bash
curl -s http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"hung@example.com","password":"strong-password"}'
```

Use the returned `accessToken`:

```bash
export TOKEN='<accessToken>'
```

Create a post:

```bash
curl -s http://localhost:8080/api/v1/posts \
  -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"title":"First post","content":"Production vote system"}'
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

## Why one database now?

The first version uses one PostgreSQL database because it keeps transactions, development, and deployment simple. Domain packages own their tables and avoid leaking repositories into other controllers. When a domain is extracted, its tables can be migrated to a service-owned database and communication can move to APIs or events.

## Next milestones

1. Refresh tokens and session revocation
2. Post ownership, update, and delete
3. Redis rate limiting and hot ranking
4. Outbox events
5. WebSocket/SSE fan-out where realtime delivery is justified
6. Metrics dashboards and load tests
