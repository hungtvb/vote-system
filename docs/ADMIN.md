# Admin foundation

This document covers the authorization boundary and controlled bootstrap introduced by TON-192. It does not describe the later moderation dashboard, audit log or operational actions from the parent TON-109 roadmap.

## Roles and authorization

Vote System accepts two application roles:

```text
USER
ADMIN
```

Registration and social onboarding always create `USER` accounts. Clients cannot request a role during registration or profile updates.

Every route under `/api/v1/admin/**` requires `ROLE_ADMIN` in the Spring Security request chain. Admin controller methods also use `@PreAuthorize("hasRole('ADMIN')")` as defense in depth.

Expected boundary behavior:

```text
anonymous request       -> 401 Unauthorized
authenticated USER      -> 403 Forbidden
authenticated ADMIN     -> allowed by the admin boundary
```

Access tokens carry a `roles` claim. The resource server maps each value to a Spring Security `ROLE_*` authority.

## Probe endpoint

```http
GET /api/v1/admin/probe
Authorization: Bearer <admin access token>
```

Successful response:

```json
{
  "status": "ok"
}
```

The probe verifies the request-level matcher, JWT role conversion and method-level guard. It is not a general health endpoint and does not expose infrastructure details.

## Controlled administrator bootstrap

Bootstrap is disabled by default:

```dotenv
ADMIN_BOOTSTRAP_ENABLED=false
ADMIN_BOOTSTRAP_EMAIL=
```

It only promotes an existing account selected by normalized email. It never creates an account, password, refresh token or OAuth identity.

### Promotion procedure

1. Register or sign in normally so the target account already exists as `USER`.
2. Set `ADMIN_BOOTSTRAP_EMAIL` to that account's email.
3. Set `ADMIN_BOOTSTRAP_ENABLED=true`.
4. Deploy the backend once.
5. Confirm the deployment starts and the account can obtain a fresh token with role `ADMIN`.
6. Set `ADMIN_BOOTSTRAP_ENABLED=false` and clear `ADMIN_BOOTSTRAP_EMAIL`.
7. Deploy again so routine restarts no longer execute bootstrap logic.

Promotion is idempotent: an account already holding `ADMIN` remains unchanged. Existing access tokens retain their original claims until login or refresh issues a new token.

### Failure behavior

When bootstrap is enabled:

- a blank or invalid email causes startup to fail;
- a target account that does not exist causes startup to fail;
- logs describe the bootstrap result without printing the configured email, passwords or tokens.

This fail-closed behavior prevents a deployment from appearing healthy when the requested administrator promotion did not happen.

## Verification

Backend tests cover:

- anonymous `401`;
- authenticated `USER` `403`;
- issued `ADMIN` JWT accepted by both guards;
- registration response remains `USER`;
- disabled bootstrap performs no repository work;
- normalized-email promotion;
- missing and invalid target handling;
- repeated bootstrap is idempotent.
