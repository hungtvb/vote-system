CREATE TABLE system_status (
    singleton_id SMALLINT PRIMARY KEY,
    mode VARCHAR(16) NOT NULL,
    message_vi VARCHAR(200),
    message_en VARCHAR(200),
    estimated_end_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_system_status_singleton CHECK (singleton_id = 1),
    CONSTRAINT ck_system_status_mode CHECK (mode IN ('NORMAL', 'READ_ONLY', 'MAINTENANCE')),
    CONSTRAINT ck_system_status_message_vi CHECK (message_vi IS NULL OR CHAR_LENGTH(BTRIM(message_vi)) BETWEEN 1 AND 200),
    CONSTRAINT ck_system_status_message_en CHECK (message_en IS NULL OR CHAR_LENGTH(BTRIM(message_en)) BETWEEN 1 AND 200)
);

INSERT INTO system_status (
    singleton_id,
    mode,
    message_vi,
    message_en,
    estimated_end_at,
    updated_at,
    updated_by,
    version
) VALUES (1, 'NORMAL', NULL, NULL, NULL, CURRENT_TIMESTAMP, NULL, 0);

ALTER TABLE admin_audit_logs DROP CONSTRAINT ck_admin_audit_logs_action_target;
ALTER TABLE admin_audit_logs DROP CONSTRAINT ck_admin_audit_logs_action;
ALTER TABLE admin_audit_logs DROP CONSTRAINT ck_admin_audit_logs_target_type;

ALTER TABLE admin_audit_logs
    ADD CONSTRAINT ck_admin_audit_logs_action CHECK (action IN (
        'ADMIN_HIDE_POST',
        'ADMIN_RESTORE_POST',
        'ADMIN_DELETE_POST',
        'ADMIN_SUSPEND_USER',
        'ADMIN_BAN_USER',
        'ADMIN_RESTORE_USER',
        'ADMIN_REVOKE_SESSIONS',
        'ADMIN_REBUILD_RANKING',
        'SYSTEM_MODE_CHANGED'
    )),
    ADD CONSTRAINT ck_admin_audit_logs_target_type CHECK (target_type IN ('POST', 'USER', 'RANKING', 'SYSTEM')),
    ADD CONSTRAINT ck_admin_audit_logs_action_target CHECK (
        (action IN ('ADMIN_HIDE_POST', 'ADMIN_RESTORE_POST', 'ADMIN_DELETE_POST') AND target_type = 'POST')
        OR (action IN ('ADMIN_SUSPEND_USER', 'ADMIN_BAN_USER', 'ADMIN_RESTORE_USER', 'ADMIN_REVOKE_SESSIONS') AND target_type = 'USER')
        OR (action = 'ADMIN_REBUILD_RANKING' AND target_type = 'RANKING')
        OR (action = 'SYSTEM_MODE_CHANGED' AND target_type = 'SYSTEM')
    );
