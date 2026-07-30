ALTER TABLE users
    ADD COLUMN account_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN status_until TIMESTAMPTZ,
    ADD COLUMN status_updated_at TIMESTAMPTZ;

ALTER TABLE users
    ADD CONSTRAINT ck_users_account_status
        CHECK (account_status IN ('ACTIVE', 'SUSPENDED', 'BANNED')),
    ADD CONSTRAINT ck_users_active_status_until
        CHECK (account_status <> 'ACTIVE' OR status_until IS NULL),
    ADD CONSTRAINT ck_users_restriction_timestamp
        CHECK (account_status = 'ACTIVE' OR status_updated_at IS NOT NULL);

CREATE INDEX idx_users_active_admin_guard
    ON users (role, account_status, status_until)
    WHERE role = 'ADMIN';

ALTER TABLE admin_audit_logs
    DROP CONSTRAINT ck_admin_audit_logs_action,
    DROP CONSTRAINT ck_admin_audit_logs_action_target;

ALTER TABLE admin_audit_logs
    ADD CONSTRAINT ck_admin_audit_logs_action CHECK (action IN (
        'ADMIN_HIDE_POST',
        'ADMIN_RESTORE_POST',
        'ADMIN_DELETE_POST',
        'ADMIN_SUSPEND_USER',
        'ADMIN_BAN_USER',
        'ADMIN_RESTORE_USER',
        'ADMIN_REVOKE_SESSIONS',
        'ADMIN_REBUILD_RANKING'
    )),
    ADD CONSTRAINT ck_admin_audit_logs_action_target CHECK (
        (action IN ('ADMIN_HIDE_POST', 'ADMIN_RESTORE_POST', 'ADMIN_DELETE_POST') AND target_type = 'POST')
        OR (action IN ('ADMIN_SUSPEND_USER', 'ADMIN_BAN_USER', 'ADMIN_RESTORE_USER', 'ADMIN_REVOKE_SESSIONS') AND target_type = 'USER')
        OR (action = 'ADMIN_REBUILD_RANKING' AND target_type = 'RANKING')
    );
