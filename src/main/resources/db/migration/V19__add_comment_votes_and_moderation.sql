ALTER TABLE comments
    ADD COLUMN moderation_updated_at TIMESTAMPTZ,
    ADD COLUMN vote_score BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN up_votes BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN down_votes BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_comments_vote_counts CHECK (up_votes >= 0 AND down_votes >= 0),
    ADD CONSTRAINT ck_comments_vote_score CHECK (vote_score = up_votes - down_votes);

CREATE TABLE comment_votes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    comment_id UUID NOT NULL REFERENCES comments(id) ON DELETE CASCADE,
    vote_type VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_comment_votes_user_comment UNIQUE (user_id, comment_id),
    CONSTRAINT ck_comment_votes_type CHECK (vote_type IN ('UP', 'DOWN'))
);

CREATE INDEX idx_comment_votes_comment_type
    ON comment_votes (comment_id, vote_type, user_id);

CREATE INDEX idx_comment_votes_user
    ON comment_votes (user_id, updated_at DESC, id DESC);

ALTER TABLE moderation_cases DROP CONSTRAINT ck_moderation_cases_resolution_action;
ALTER TABLE moderation_cases
    ADD CONSTRAINT ck_moderation_cases_resolution_action
        CHECK (resolution_action IS NULL OR resolution_action IN (
            'HIDE_BALLOT',
            'RESTORE_BALLOT',
            'DELETE_BALLOT',
            'HIDE_COMMENT',
            'RESTORE_COMMENT',
            'DELETE_COMMENT',
            'SUSPEND_USER',
            'BAN_USER',
            'RESTORE_USER'
        ));

ALTER TABLE moderation_cases DROP CONSTRAINT ck_moderation_cases_action_target;
ALTER TABLE moderation_cases
    ADD CONSTRAINT ck_moderation_cases_action_target CHECK (
        resolution_action IS NULL
        OR (target_type = 'BALLOT' AND resolution_action IN ('HIDE_BALLOT', 'RESTORE_BALLOT', 'DELETE_BALLOT'))
        OR (target_type = 'COMMENT' AND resolution_action IN ('HIDE_COMMENT', 'RESTORE_COMMENT', 'DELETE_COMMENT'))
        OR (target_type = 'USER' AND resolution_action IN ('SUSPEND_USER', 'BAN_USER', 'RESTORE_USER'))
    );

ALTER TABLE admin_audit_logs DROP CONSTRAINT ck_admin_audit_logs_action_target;
ALTER TABLE admin_audit_logs DROP CONSTRAINT ck_admin_audit_logs_action;
ALTER TABLE admin_audit_logs DROP CONSTRAINT ck_admin_audit_logs_target_type;
ALTER TABLE admin_audit_logs
    ADD CONSTRAINT ck_admin_audit_logs_action CHECK (action IN (
        'ADMIN_HIDE_POST',
        'ADMIN_RESTORE_POST',
        'ADMIN_DELETE_POST',
        'ADMIN_HIDE_COMMENT',
        'ADMIN_RESTORE_COMMENT',
        'ADMIN_DELETE_COMMENT',
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
        CHECK (target_type IN ('POST', 'COMMENT', 'USER', 'RANKING', 'SYSTEM', 'MODERATION_CASE')),
    ADD CONSTRAINT ck_admin_audit_logs_action_target CHECK (
        (action IN ('ADMIN_HIDE_POST', 'ADMIN_RESTORE_POST', 'ADMIN_DELETE_POST') AND target_type = 'POST')
        OR (action IN ('ADMIN_HIDE_COMMENT', 'ADMIN_RESTORE_COMMENT', 'ADMIN_DELETE_COMMENT') AND target_type = 'COMMENT')
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

DO $$
DECLARE
    browser_role TEXT;
BEGIN
    FOREACH browser_role IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = browser_role) THEN
            EXECUTE format('REVOKE ALL PRIVILEGES ON TABLE public.comment_votes FROM %I', browser_role);
        END IF;
    END LOOP;
END
$$;

ALTER TABLE public.comment_votes ENABLE ROW LEVEL SECURITY;
