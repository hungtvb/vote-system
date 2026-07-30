CREATE INDEX idx_users_admin_created
    ON users (created_at DESC, id DESC);

CREATE INDEX idx_posts_admin_moderation_created
    ON posts (moderation_status, created_at DESC, id DESC);
