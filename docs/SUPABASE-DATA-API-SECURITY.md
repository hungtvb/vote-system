# Supabase Data API security boundary

Vote System uses Supabase PostgreSQL as the database, but application data is owned exclusively by the Spring backend. The browser frontend must call the Spring API and must not query Vote System tables through Supabase PostgREST.

Two migration paths preserve this boundary:

- `supabase/migrations/20260802054537_lock_vote_system_data_api.sql` is the provider migration and protects all nine exposed tables, including `flyway_schema_history`.
- Flyway `V15__lock_supabase_data_api.sql` protects the eight application tables in every Spring-managed environment and fixes the related function/index findings.

The protected tables are:

- `flyway_schema_history`
- `users`
- `posts`
- `votes`
- `refresh_sessions`
- `user_identities`
- `admin_audit_logs`
- `ranking_revision`
- `system_status`

For every table, the Supabase provider migration enables Row Level Security and revokes all table privileges from roles `anon` and `authenticated`. No browser RLS policy is created intentionally, so PostgREST access remains denied by default.

`flyway_schema_history` is deliberately excluded from Flyway V15. Flyway holds its migration lock around the schema history table while versioned migrations run, so altering that table from inside V15 can wait on Flyway's own lock. The provider migration records and applies that infrastructure-specific hardening safely outside the Flyway execution path.

Flyway V15 checks whether the Supabase roles exist before revoking privileges. This keeps local and CI PostgreSQL containers compatible. It also sets a finite PostgreSQL lock timeout so a contended deployment fails visibly instead of waiting indefinitely.

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
