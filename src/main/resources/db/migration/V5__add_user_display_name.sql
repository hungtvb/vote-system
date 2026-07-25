ALTER TABLE users
    ADD COLUMN display_name VARCHAR(80);

UPDATE users
SET display_name = 'Voter ' || UPPER(SUBSTRING(REPLACE(id::text, '-', '') FROM 1 FOR 8))
WHERE display_name IS NULL;

ALTER TABLE users
    ALTER COLUMN display_name SET NOT NULL,
    ADD CONSTRAINT ck_users_display_name_not_blank CHECK (BTRIM(display_name) <> '');
