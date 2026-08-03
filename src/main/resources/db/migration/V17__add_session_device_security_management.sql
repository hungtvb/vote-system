ALTER TABLE refresh_sessions
    ADD COLUMN family_id UUID,
    ADD COLUMN started_at TIMESTAMPTZ,
    ADD COLUMN provider VARCHAR(16),
    ADD COLUMN client_label VARCHAR(64);

UPDATE refresh_sessions
   SET family_id = id,
       started_at = created_at,
       provider = 'UNKNOWN',
       client_label = 'UNKNOWN'
 WHERE family_id IS NULL;

ALTER TABLE refresh_sessions
    ALTER COLUMN family_id SET NOT NULL,
    ALTER COLUMN started_at SET NOT NULL,
    ALTER COLUMN provider SET NOT NULL,
    ALTER COLUMN client_label SET NOT NULL,
    ADD CONSTRAINT ck_refresh_sessions_provider
        CHECK (provider IN ('UNKNOWN', 'PASSWORD', 'GOOGLE', 'GITHUB')),
    ADD CONSTRAINT ck_refresh_sessions_client_label
        CHECK (CHAR_LENGTH(client_label) BETWEEN 1 AND 64),
    ADD CONSTRAINT ck_refresh_sessions_started_at
        CHECK (started_at <= created_at);

CREATE UNIQUE INDEX uq_refresh_sessions_active_family
    ON refresh_sessions (family_id)
    WHERE revoked_at IS NULL;

CREATE INDEX idx_refresh_sessions_user_active_device
    ON refresh_sessions (user_id, started_at DESC, family_id)
    WHERE revoked_at IS NULL;

CREATE TABLE account_security_events (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_type VARCHAR(40) NOT NULL,
    session_family_id UUID,
    provider VARCHAR(16),
    client_label VARCHAR(64),
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_account_security_events_type CHECK (event_type IN (
        'SIGN_IN',
        'SESSION_REVOKED',
        'SUSPICIOUS_TOKEN_REUSE',
        'EMAIL_VERIFICATION_REQUESTED',
        'ACCOUNT_RECOVERY_REQUESTED'
    )),
    CONSTRAINT ck_account_security_events_provider
        CHECK (provider IS NULL OR provider IN ('UNKNOWN', 'PASSWORD', 'GOOGLE', 'GITHUB')),
    CONSTRAINT ck_account_security_events_client_label
        CHECK (client_label IS NULL OR CHAR_LENGTH(client_label) BETWEEN 1 AND 64)
);

CREATE INDEX idx_account_security_events_user_time
    ON account_security_events (user_id, occurred_at DESC, id DESC);

DO $$
DECLARE
    browser_role TEXT;
BEGIN
    FOREACH browser_role IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = browser_role) THEN
            EXECUTE format('REVOKE ALL PRIVILEGES ON TABLE public.account_security_events FROM %I', browser_role);
        END IF;
    END LOOP;
END
$$;

ALTER TABLE public.account_security_events ENABLE ROW LEVEL SECURITY;
