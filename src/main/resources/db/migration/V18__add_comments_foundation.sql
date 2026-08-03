ALTER TABLE posts
    ADD COLUMN comment_count BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_posts_comment_count CHECK (comment_count >= 0);

CREATE TABLE comments (
    id UUID PRIMARY KEY,
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    parent_id UUID REFERENCES comments(id) ON DELETE RESTRICT,
    body VARCHAR(2000) NOT NULL,
    moderation_status VARCHAR(32) NOT NULL,
    edited_at TIMESTAMPTZ,
    removed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_comments_body
        CHECK (CHAR_LENGTH(BTRIM(body)) BETWEEN 1 AND 2000),
    CONSTRAINT ck_comments_moderation_status
        CHECK (moderation_status IN ('VISIBLE', 'REMOVED_BY_AUTHOR', 'HIDDEN', 'DELETED')),
    CONSTRAINT ck_comments_parent_not_self
        CHECK (parent_id IS NULL OR parent_id <> id),
    CONSTRAINT ck_comments_removed_at CHECK (
        (moderation_status = 'VISIBLE' AND removed_at IS NULL)
        OR (moderation_status <> 'VISIBLE' AND removed_at IS NOT NULL)
    )
);

CREATE INDEX idx_comments_post_cursor
    ON comments (post_id, created_at ASC, id ASC);

CREATE INDEX idx_comments_parent_cursor
    ON comments (parent_id, created_at ASC, id ASC)
    WHERE parent_id IS NOT NULL;

CREATE INDEX idx_comments_author
    ON comments (author_id, created_at DESC, id DESC);

-- Comments and account data remain backend-owned and unavailable through Supabase browser roles.
DO $$
DECLARE
    browser_role TEXT;
BEGIN
    FOREACH browser_role IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = browser_role) THEN
            EXECUTE format('REVOKE ALL PRIVILEGES ON TABLE public.comments FROM %I', browser_role);
        END IF;
    END LOOP;
END
$$;

ALTER TABLE public.comments ENABLE ROW LEVEL SECURITY;
