ALTER TABLE users
    ADD COLUMN bio VARCHAR(160) NOT NULL DEFAULT '',
    ADD COLUMN avatar_icon VARCHAR(32) NOT NULL DEFAULT 'CITIZEN',
    ADD COLUMN avatar_color VARCHAR(32) NOT NULL DEFAULT 'NAVY',
    ADD COLUMN preferred_locale VARCHAR(2) NOT NULL DEFAULT 'VI';

ALTER TABLE users
    ADD CONSTRAINT ck_users_bio_length CHECK (CHAR_LENGTH(bio) <= 160),
    ADD CONSTRAINT ck_users_avatar_icon CHECK (avatar_icon IN (
        'CITIZEN', 'ADVOCATE', 'THINKER', 'ORGANIZER', 'VOLUNTEER',
        'CREATOR', 'LEADER', 'ANALYST', 'VISIONARY', 'BUILDER'
    )),
    ADD CONSTRAINT ck_users_avatar_color CHECK (avatar_color IN (
        'NAVY', 'SEAL', 'KRAFT', 'GRAPHITE', 'MOSS', 'INK_BLUE'
    )),
    ADD CONSTRAINT ck_users_preferred_locale CHECK (preferred_locale IN ('VI', 'EN'));
