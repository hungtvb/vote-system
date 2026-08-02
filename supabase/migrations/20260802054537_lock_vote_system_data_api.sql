-- Supabase provider migration for the backend-only Vote System schema.
-- This file is the source-of-truth for locking Flyway's own history table in
-- addition to the eight application tables. It must be applied through the
-- Supabase migration path, not from inside a Flyway versioned migration.
SET LOCAL lock_timeout = '10s';

REVOKE ALL PRIVILEGES ON TABLE public.flyway_schema_history FROM anon, authenticated;
REVOKE ALL PRIVILEGES ON TABLE public.users FROM anon, authenticated;
REVOKE ALL PRIVILEGES ON TABLE public.posts FROM anon, authenticated;
REVOKE ALL PRIVILEGES ON TABLE public.votes FROM anon, authenticated;
REVOKE ALL PRIVILEGES ON TABLE public.refresh_sessions FROM anon, authenticated;
REVOKE ALL PRIVILEGES ON TABLE public.user_identities FROM anon, authenticated;
REVOKE ALL PRIVILEGES ON TABLE public.admin_audit_logs FROM anon, authenticated;
REVOKE ALL PRIVILEGES ON TABLE public.ranking_revision FROM anon, authenticated;
REVOKE ALL PRIVILEGES ON TABLE public.system_status FROM anon, authenticated;

ALTER TABLE public.flyway_schema_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.posts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.votes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.refresh_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_identities ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.admin_audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ranking_revision ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.system_status ENABLE ROW LEVEL SECURITY;

ALTER FUNCTION public.prevent_admin_audit_log_mutation()
    SET search_path = public, pg_temp;

CREATE INDEX IF NOT EXISTS idx_refresh_sessions_replaced_by_session_id
    ON public.refresh_sessions (replaced_by_session_id)
    WHERE replaced_by_session_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_system_status_updated_by
    ON public.system_status (updated_by)
    WHERE updated_by IS NOT NULL;
