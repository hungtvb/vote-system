# TON-177 verification

This document defines the evidence boundary for maintenance-mode recovery and regression QA. Evidence is valid only for the exact commit and deployment identifiers recorded with it.

## Automated CI matrix

The primary CI workflow must complete all of these checks on the current pull-request head:

### Backend

- full Maven verification;
- focused system-mode unit/integration coverage;
- bounded `vote.system.mode.requests` tag tests;
- production image build;
- production-profile runtime smoke.

The runtime smoke must prove:

1. a normal USER receives `403` for administrator status read/update;
2. a controlled bootstrap promotes an existing active account without exposing its email;
3. the existing refresh cookie restores the promoted `ADMIN` session;
4. `MAINTENANCE` rejects public feed traffic with `503 SYSTEM_MAINTENANCE` and `Retry-After`;
5. CORS preflight, health, public status, refresh, and protected administrator recovery remain reachable;
6. the mode survives a backend restart because PostgreSQL is authoritative;
7. stopping Redis does not change the authoritative mode; security-critical refresh fails closed with `RATE_LIMIT_UNAVAILABLE`, while the already-restored administrator token can still use the recovery endpoint;
8. returning to `NORMAL` restores public reads and a real vote write;
9. enable/disable audit records correlate with bounded request IDs;
10. the rejection metric exposes bounded code/mode/method/route/status tags;
11. uploaded logs and artifacts contain no bootstrap email, refresh cookie, or JWT.

### Frontend

- 72 shared TypeScript lifecycle/model tests;
- production Next.js build;
- Ballot Edition contrast and asset checks;
- READ_ONLY banner checks at 1440, 768, 390, 375, and 320 px;
- authenticated READ_ONLY write-control checks;
- MAINTENANCE notice checks at the same viewports;
- long administrator-authored VI/EN copy without horizontal overflow;
- retry transition back to NORMAL;
- isolated stateful fixtures so screenshot and DOM capture replay the same mode transition;
- keyboard touch-target, reduced-motion, and visual/accessibility baseline checks.

## Controlled deployed-topology checks

Run after the exact release is deployed to:

```text
Frontend   Vercel  https://app.ballotbox.io.vn
Backend    Railway https://api.ballotbox.io.vn
Database   Supabase PostgreSQL
Cache      managed Redis (derived infrastructure)
```

Safe read-only probes may run at any time:

- Vercel page/assets and required security headers;
- Railway `/actuator/health`;
- public `/api/v1/system/status`;
- anonymous/USER denial for protected admin and Actuator endpoints;
- Swagger/OpenAPI unavailable in production;
- public feed and CORS response behavior.

Changing production to READ_ONLY or MAINTENANCE requires a controlled maintenance window, an active administrator recovery session, an incident/change identifier, and a rollback owner. Do not infer this lifecycle from CI alone and do not mutate production merely to satisfy a pull-request gate.

## Completion rule

Move TON-177 to In Review only after the current PR head is green and evidence is attached. Mark it Done only after:

- the PR is merged;
- the relevant release is deployed;
- the controlled deployed-topology recovery lifecycle is recorded, or the product owner explicitly accepts CI/staging evidence in place of a live production mutation;
- unresolved risks and the emergency database procedure have been reviewed.
