# Documentation maintenance

This document defines when Vote System documentation must change and how reviewers verify freshness.

## Source-of-truth order

When documentation and implementation disagree, resolve the conflict in this order:

1. Flyway migrations, controller mappings, DTOs, security configuration, runtime source, and active profile configuration.
2. Generated OpenAPI from a non-production environment on the same commit.
3. Repository documentation.
4. Historical PR descriptions, Linear issue text, and design exports.

Production secrets and platform settings are not inferred from repository defaults.

## Documentation Definition of Done

A PR must update documentation when it changes any of these contracts:

- endpoint, method, authentication rule, DTO, validation, or error behavior;
- refresh cookie, OAuth, CORS, role, permission, or privacy boundary;
- Flyway schema or durable/derived data ownership;
- feed ordering, ranking fallback, rate limiting, SSE, cache, or async side effects;
- production topology, required environment variables, active profile, or deployment verification;
- metric names, tag rules, request logging, health checks, or operational runbooks;
- locale behavior, message catalogs, profile fields, or public identity behavior;
- Ballot Edition interaction, accessibility, responsive, or reusable-component rules;
- roadmap/dependency claims shown in current-state documents.

For a UI-only or internal refactor with no contract impact, the PR must state `Docs: N/A` and explain why.

## Required PR declaration

Every PR should choose exactly one:

```text
Docs updated: <files and reason>
```

or:

```text
Docs: N/A — <reason>
```

The repository PR template contains this declaration.

## Review procedure

Before merge:

1. Search affected documents for stale terms such as `planned`, `future`, `TODO`, old hosting providers, old ports, and removed endpoints.
2. Verify API/security claims against controllers, DTOs, `SecurityConfig`, Flyway, and active Spring profiles.
3. Verify deployment claims against `Dockerfile.railway`, `railway.toml`, Vercel config, and environment examples.
4. Verify frontend lifecycle claims against the current API modules and session/i18n providers.
5. Keep genuine future work clearly labelled with an active Linear issue; do not present it as implemented.
6. Run documentation link and formatting checks when available, plus the normal CI gates for any source/config change.

## Freshness audit — 2026-07-29

TON-190 corrected these stale claims:

- TON-116 i18n and TON-108 Profile/Ballot Mark were still listed as upcoming work.
- `DESIGN.md` still described editable bio and Ballot Mark as unimplemented/future.
- production docs still described Swagger/OpenAPI as reachable after the production profile disabled springdoc.
- `docs/README.md` still described temporary DEBUG/Hibernate statistics as the production baseline after production hardening.
- the root API overview omitted profile update and public-profile endpoints.
- the root environment example omitted profile-update rate-limit settings.
- the completed i18n foundation had no dedicated current-behavior document.

Intentionally retained roadmap language:

- TON-109 Admin foundation;
- TON-110/111 comments and comment moderation;
- TON-112/113/114 notifications, outbox, and realtime delivery;
- TON-115 constrained direct messaging;
- later public API, analytics, and advanced ballot-engine work managed in Linear.

Known implementation gap discovered by the audit:

- TON-191 tracks browser-language fallback and automatic application of authenticated `preferredLocale`. Current docs state the actual behavior instead of claiming the unfinished behavior is active.
