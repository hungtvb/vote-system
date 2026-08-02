-- Vote System data is owned by the Spring backend. Supabase browser roles must
-- not access application tables through PostgREST.
--
-- flyway_schema_history is intentionally not altered here. Flyway holds its
-- migration lock around that table while this script runs; its Supabase-specific
-- RLS hardening lives in supabase/migrations/20260802054537_lock_vote_system_data_api.sql.
SET LOCAL lock_timeout = '10s';

DO $$
DECLARE
    target_table TEXT;
    protected_tables CONSTANT TEXT[] := ARRAY[
        'users',
        'posts',
        'votes',
        'refresh_sessions',
        'user_identities',
        'admin_audit_logs',
        'ranking_revision',
        'system_status'
    ];
BEGIN
    FOREACH target_table IN ARRAY protected_tables LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
            EXECUTE format(
                'REVOKE ALL PRIVILEGES ON TABLE public.%I FROM anon',
                target_table
            );
        END IF;

        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
            EXECUTE format(
                'REVOKE ALL PRIVILEGES ON TABLE public.%I FROM authenticated',
                target_table
            );
        END IF;
    END LOOP;
END
$$;

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
