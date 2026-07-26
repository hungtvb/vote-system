ALTER TABLE users
    ALTER COLUMN email DROP NOT NULL,
    ALTER COLUMN password_hash DROP NOT NULL;

CREATE TABLE user_identities (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    provider VARCHAR(32) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    provider_email VARCHAR(320),
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_user_identities_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_user_identities_provider_subject
        UNIQUE (provider, provider_subject),
    CONSTRAINT uk_user_identities_user_provider
        UNIQUE (user_id, provider),
    CONSTRAINT ck_user_identities_provider
        CHECK (provider IN ('GOOGLE', 'GITHUB')),
    CONSTRAINT ck_user_identities_subject_not_blank
        CHECK (BTRIM(provider_subject) <> '')
);

CREATE INDEX idx_user_identities_user_id ON user_identities (user_id);
