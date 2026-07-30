ALTER TABLE posts
    ADD COLUMN moderation_status VARCHAR(16) NOT NULL DEFAULT 'VISIBLE',
    ADD COLUMN moderation_updated_at TIMESTAMPTZ;

ALTER TABLE posts
    ADD CONSTRAINT ck_posts_moderation_status
        CHECK (moderation_status IN ('VISIBLE', 'HIDDEN', 'DELETED')),
    ADD CONSTRAINT ck_posts_moderation_timestamp
        CHECK (moderation_status = 'VISIBLE' OR moderation_updated_at IS NOT NULL);

CREATE INDEX idx_posts_public_created
    ON posts (created_at DESC, id DESC)
    WHERE moderation_status = 'VISIBLE';
