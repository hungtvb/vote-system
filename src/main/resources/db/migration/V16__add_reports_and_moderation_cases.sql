CREATE TABLE moderation_cases (
    id UUID PRIMARY KEY,
    target_type VARCHAR(16) NOT NULL,
    target_id UUID NOT NULL,
    target_validation_status VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    assignee_id UUID REFERENCES users(id) ON DELETE SET NULL,
    report_count INTEGER NOT NULL DEFAULT 1,
    resolution_action VARCHAR(32),
    resolution_reason VARCHAR(500),
    resolution_until TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_moderation_cases_target_type
        CHECK (target_type IN ('BALLOT', 'COMMENT', 'USER')),
    CONSTRAINT ck_moderation_cases_target_validation
        CHECK (target_validation_status IN ('VERIFIED', 'DEFERRED')),
    CONSTRAINT ck_moderation_cases_status
        CHECK (status IN ('OPEN', 'TRIAGED', 'IN_REVIEW', 'RESOLVED', 'REJECTED', 'REOPENED')),
    CONSTRAINT ck_moderation_cases_report_count
        CHECK (report_count > 0),
    CONSTRAINT ck_moderation_cases_resolution_action
        CHECK (resolution_action IS NULL OR resolution_action IN (
            'HIDE_BALLOT',
            'RESTORE_BALLOT',
            'DELETE_BALLOT',
            'SUSPEND_USER',
            'BAN_USER',
            'RESTORE_USER'
        )),
    CONSTRAINT ck_moderation_cases_resolution_reason
        CHECK (resolution_reason IS NULL OR CHAR_LENGTH(BTRIM(resolution_reason)) BETWEEN 1 AND 500),
    CONSTRAINT ck_moderation_cases_terminal_fields CHECK (
        (status = 'RESOLVED'
            AND resolution_action IS NOT NULL
            AND resolution_reason IS NOT NULL
            AND resolved_at IS NOT NULL)
        OR (status = 'REJECTED'
            AND resolution_action IS NULL
            AND resolution_reason IS NOT NULL
            AND resolved_at IS NOT NULL)
        OR (status IN ('OPEN', 'TRIAGED', 'IN_REVIEW', 'REOPENED')
            AND resolution_action IS NULL
            AND resolution_reason IS NULL
            AND resolution_until IS NULL
            AND resolved_at IS NULL)
    ),
    CONSTRAINT ck_moderation_cases_action_target CHECK (
        resolution_action IS NULL
        OR (target_type = 'BALLOT' AND resolution_action IN ('HIDE_BALLOT', 'RESTORE_BALLOT', 'DELETE_BALLOT'))
        OR (target_type = 'USER' AND resolution_action IN ('SUSPEND_USER', 'BAN_USER', 'RESTORE_USER'))
    )
);

CREATE UNIQUE INDEX uq_moderation_cases_active_target
    ON moderation_cases (target_type, target_id)
    WHERE status IN ('OPEN', 'TRIAGED', 'IN_REVIEW', 'REOPENED');

CREATE INDEX idx_moderation_cases_queue
    ON moderation_cases (status, created_at DESC, id DESC);

CREATE INDEX idx_moderation_cases_assignee_queue
    ON moderation_cases (assignee_id, status, created_at DESC, id DESC)
    WHERE assignee_id IS NOT NULL;

CREATE TABLE reports (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES moderation_cases(id) ON DELETE RESTRICT,
    reporter_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    target_type VARCHAR(16) NOT NULL,
    target_id UUID NOT NULL,
    reason_code VARCHAR(32) NOT NULL,
    evidence_text VARCHAR(1000) NOT NULL,
    status VARCHAR(16) NOT NULL,
    closed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_reports_target_type
        CHECK (target_type IN ('BALLOT', 'COMMENT', 'USER')),
    CONSTRAINT ck_reports_reason_code
        CHECK (reason_code IN ('SPAM', 'HARASSMENT', 'HATE_SPEECH', 'MISINFORMATION', 'IMPERSONATION', 'PRIVACY', 'OTHER')),
    CONSTRAINT ck_reports_evidence
        CHECK (CHAR_LENGTH(BTRIM(evidence_text)) BETWEEN 1 AND 1000),
    CONSTRAINT ck_reports_status
        CHECK (status IN ('OPEN', 'RESOLVED', 'REJECTED')),
    CONSTRAINT ck_reports_closed_at CHECK (
        (status = 'OPEN' AND closed_at IS NULL)
        OR (status IN ('RESOLVED', 'REJECTED') AND closed_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_reports_active_duplicate
    ON reports (reporter_id, target_type, target_id, reason_code)
    WHERE status = 'OPEN';

CREATE INDEX idx_reports_reporter_history
    ON reports (reporter_id, created_at DESC, id DESC);

CREATE INDEX idx_reports_case
    ON reports (case_id, created_at ASC, id ASC);

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
        'SYSTEM_MODE_CHANGED',
        'ADMIN_ASSIGN_MODERATION_CASE',
        'ADMIN_TRIAGE_MODERATION_CASE',
        'ADMIN_REVIEW_MODERATION_CASE',
        'ADMIN_RESOLVE_MODERATION_CASE',
        'ADMIN_REJECT_MODERATION_CASE',
        'ADMIN_REOPEN_MODERATION_CASE'
    )),
    ADD CONSTRAINT ck_admin_audit_logs_target_type
        CHECK (target_type IN ('POST', 'USER', 'RANKING', 'SYSTEM', 'MODERATION_CASE')),
    ADD CONSTRAINT ck_admin_audit_logs_action_target CHECK (
        (action IN ('ADMIN_HIDE_POST', 'ADMIN_RESTORE_POST', 'ADMIN_DELETE_POST') AND target_type = 'POST')
        OR (action IN ('ADMIN_SUSPEND_USER', 'ADMIN_BAN_USER', 'ADMIN_RESTORE_USER', 'ADMIN_REVOKE_SESSIONS') AND target_type = 'USER')
        OR (action = 'ADMIN_REBUILD_RANKING' AND target_type = 'RANKING')
        OR (action = 'SYSTEM_MODE_CHANGED' AND target_type = 'SYSTEM')
        OR (action IN (
            'ADMIN_ASSIGN_MODERATION_CASE',
            'ADMIN_TRIAGE_MODERATION_CASE',
            'ADMIN_REVIEW_MODERATION_CASE',
            'ADMIN_RESOLVE_MODERATION_CASE',
            'ADMIN_REJECT_MODERATION_CASE',
            'ADMIN_REOPEN_MODERATION_CASE'
        ) AND target_type = 'MODERATION_CASE')
    );

-- Keep newly introduced backend-owned tables outside the Supabase browser Data API.
DO $$
DECLARE
    browser_role TEXT;
BEGIN
    FOREACH browser_role IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = browser_role) THEN
            EXECUTE format('REVOKE ALL PRIVILEGES ON TABLE public.moderation_cases FROM %I', browser_role);
            EXECUTE format('REVOKE ALL PRIVILEGES ON TABLE public.reports FROM %I', browser_role);
        END IF;
    END LOOP;
END
$$;

ALTER TABLE public.moderation_cases ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reports ENABLE ROW LEVEL SECURITY;
