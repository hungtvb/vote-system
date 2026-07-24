# ADR 0001: Use a modular monolith

- **Status:** Accepted
- **Date:** 2026-07-24

## Context

The platform needs transactional consistency across posts and votes, inexpensive deployment, and fast iteration. The product does not yet have independently scaling workloads or separate owning teams that justify distributed services.

## Decision

Implement the backend as one Spring Boot deployable with explicit domain packages for authentication, posts, votes, rate limiting, and shared infrastructure. Each module owns its repositories and exposes behavior through services. Controllers must not access repositories owned by another module.

Use one PostgreSQL database for the current deployable. Domain ownership is represented by package and table ownership conventions rather than separate databases.

## Consequences

### Positive

- Local transactions preserve vote and score consistency.
- Deployment and observability remain simple.
- Infrastructure cost stays low.
- Refactoring across modules is easier while the domain is still evolving.

### Negative

- Modules cannot scale independently.
- A process-level failure affects all domains.
- Boundary discipline must be enforced by code review and tests.

## Extraction trigger

A module may be extracted only when there is measured independent scaling, release, reliability, security, or ownership pressure. Extraction must include data ownership migration and use events/outbox for cross-service consistency.