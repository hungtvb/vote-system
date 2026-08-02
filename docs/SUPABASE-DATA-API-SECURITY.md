# Supabase Data API security boundary

Vote System uses Supabase PostgreSQL as the database, but application data is owned exclusively by the Spring backend. The browser frontend must call the Spring API and must not query Vote System tables through Supabase PostgREST.

Flyway V15 protects the backend-owned tables:

- `flyway_schema_history`
- `users`
- `posts`
- `votes`
- `refresh_sessions`
- `user_identities`
- `admin_audit_logs`
- `ranking_revision`
- `system_status`

For every table, V15 enables Row Level Security and revokes all table privileges from Supabase roles `anon` and `authenticated`. No browser RLS policy is created intentionally, so PostgREST access remains denied by default.

The migration checks whether the Supabase roles exist before revoking privileges. This keeps local and CI PostgreSQL containers compatible while applying the stronger boundary automatically on Supabase.

The Railway backend connects through the Supabase PostgreSQL session pooler using the database owner mapping. Spring Data and Flyway therefore remain authoritative after RLS is enabled.

## Production verification

The release is valid only when all nine tables report:

```text
rls_enabled = true
anon SELECT/INSERT/UPDATE/DELETE = false
authenticated SELECT/INSERT/UPDATE/DELETE = false
```

Supabase security advisor may report `RLS Enabled No Policy` at INFO level. That result is expected for backend-only tables and must not be "fixed" by adding browser policies.

Do not expose a Supabase publishable key as an alternate data path for these tables. Any future direct-client feature must use a dedicated table with explicit least-privilege policies and a separate security review.
