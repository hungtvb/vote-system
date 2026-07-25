ALTER TABLE posts
    ADD COLUMN ballot_number VARCHAR(32),
    ADD COLUMN category VARCHAR(50) NOT NULL DEFAULT 'GENERAL',
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    ADD COLUMN closes_at TIMESTAMPTZ,
    ADD COLUMN verdict_threshold INTEGER NOT NULL DEFAULT 70,
    ADD COLUMN final_verdict VARCHAR(16),
    ADD COLUMN closed_at TIMESTAMPTZ;

UPDATE posts
SET ballot_number = 'BAL-' || UPPER(SUBSTRING(REPLACE(id::text, '-', '') FROM 1 FOR 8))
WHERE ballot_number IS NULL;

ALTER TABLE posts
    ALTER COLUMN ballot_number SET NOT NULL,
    ADD CONSTRAINT uq_posts_ballot_number UNIQUE (ballot_number),
    ADD CONSTRAINT ck_posts_status CHECK (status IN ('OPEN', 'CLOSED')),
    ADD CONSTRAINT ck_posts_verdict_threshold CHECK (verdict_threshold BETWEEN 50 AND 100),
    ADD CONSTRAINT ck_posts_final_verdict CHECK (final_verdict IS NULL OR final_verdict IN ('UP', 'DOWN', 'UNDECIDED')),
    ADD CONSTRAINT ck_posts_closed_state CHECK (
        (status = 'OPEN' AND closed_at IS NULL AND final_verdict IS NULL)
        OR (status = 'CLOSED' AND closed_at IS NOT NULL AND final_verdict IS NOT NULL)
    );

CREATE INDEX idx_posts_status_closes_at ON posts (status, closes_at);
CREATE INDEX idx_posts_category_created_at ON posts (category, created_at DESC);
