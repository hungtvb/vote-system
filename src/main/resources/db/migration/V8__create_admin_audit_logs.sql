CREATE TABLE admin_audit_logs (
    id UUID PRIMARY KEY,
    actor_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id VARCHAR(128) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_admin_audit_logs_action CHECK (action IN (
        'ADMIN_HIDE_POST',
        'ADMIN_RESTORE_POST',
        'ADMIN_DELETE_POST',
        'ADMIN_SUSPEND_USER',
        'ADMIN_BAN_USER',
        'ADMIN_REVOKE_SESSIONS',
        'ADMIN_REBUILD_RANKING'
    )),
    CONSTRAINT ck_admin_audit_logs_target_type CHECK (target_type IN ('POST', 'USER', 'RANKING')),
    CONSTRAINT ck_admin_audit_logs_action_target CHECK (
        (action IN ('ADMIN_HIDE_POST', 'ADMIN_RESTORE_POST', 'ADMIN_DELETE_POST') AND target_type = 'POST')
        OR (action IN ('ADMIN_SUSPEND_USER', 'ADMIN_BAN_USER', 'ADMIN_REVOKE_SESSIONS') AND target_type = 'USER')
        OR (action = 'ADMIN_REBUILD_RANKING' AND target_type = 'RANKING')
    ),
    CONSTRAINT ck_admin_audit_logs_target_id CHECK (CHAR_LENGTH(BTRIM(target_id)) BETWEEN 1 AND 128),
    CONSTRAINT ck_admin_audit_logs_reason CHECK (CHAR_LENGTH(BTRIM(reason)) BETWEEN 1 AND 500),
    CONSTRAINT ck_admin_audit_logs_metadata CHECK (
        JSONB_TYPEOF(metadata) = 'object'
        AND OCTET_LENGTH(metadata::text) <= 4096
    )
);

CREATE INDEX idx_admin_audit_logs_created
    ON admin_audit_logs (created_at DESC, id DESC);
CREATE INDEX idx_admin_audit_logs_actor_created
    ON admin_audit_logs (actor_id, created_at DESC, id DESC);
CREATE INDEX idx_admin_audit_logs_action_created
    ON admin_audit_logs (action, created_at DESC, id DESC);
CREATE INDEX idx_admin_audit_logs_target_created
    ON admin_audit_logs (target_type, target_id, created_at DESC, id DESC);

CREATE OR REPLACE FUNCTION prevent_admin_audit_log_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'admin audit logs are append-only' USING ERRCODE = '55000';
    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_admin_audit_logs_append_only
BEFORE UPDATE OR DELETE ON admin_audit_logs
FOR EACH ROW
EXECUTE FUNCTION prevent_admin_audit_log_mutation();
