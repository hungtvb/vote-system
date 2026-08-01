ALTER TABLE users
    ADD COLUMN security_version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN users.security_version IS
    'Monotonic version embedded in access JWTs; incrementing revokes previously issued tokens.';
