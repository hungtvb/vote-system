# Vote System

A production-oriented side project built as a **modular monolith**, so the auth, post, and vote domains can be extracted into separate services later without rewriting the whole application.

## Stack

- Java 21
- Spring Boot 3.5.14
- Spring Security + signed JWT bearer tokens
- Spring Data JPA
- PostgreSQL 17
- Flyway
- Testcontainers
- Docker Compose

## Current capabilities

- Register and login
- BCrypt password hashing
- Stateless JWT authentication
- Create and read posts
- Upvote, downvote, change vote, and remove vote
- One vote per user/post enforced by a database constraint
- Atomic post score updates
- RFC 9457-style problem responses
- PostgreSQL integration test through Testcontainers

## Run locally

Requirements: Java 21, Maven 3.6.3+, and Docker.

```bash
docker compose up -d postgres
mvn spring-boot:run
```

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

The first version uses one PostgreSQL database because it keeps transactions, development, and deployment simple. Domain packages own their tables and do not expose repositories across controllers. When a domain is extracted, its tables can be migrated to a service-owned database and communication can move to APIs/events.

## Next milestones

1. Refresh tokens and session revocation
2. Post ownership/update/delete
3. Redis rate limiting and hot ranking
4. Outbox events
5. WebSocket/SSE updates where realtime fan-out is justified
6. Metrics dashboards and load tests
