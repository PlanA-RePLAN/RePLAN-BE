ALTER TABLE users ADD COLUMN created_at TIMESTAMP;
UPDATE users SET created_at = updated_at WHERE created_at IS NULL AND updated_at IS NOT NULL;
UPDATE users SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;

ALTER TABLE goal ADD COLUMN created_at TIMESTAMP;
UPDATE goal SET created_at = updated_at WHERE created_at IS NULL AND updated_at IS NOT NULL;
UPDATE goal SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;

ALTER TABLE tag ADD COLUMN created_at TIMESTAMP;
UPDATE tag SET created_at = updated_at WHERE created_at IS NULL AND updated_at IS NOT NULL;
UPDATE tag SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;

ALTER TABLE routine ADD COLUMN created_at TIMESTAMP;
UPDATE routine SET created_at = updated_at WHERE created_at IS NULL AND updated_at IS NOT NULL;
UPDATE routine SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;

ALTER TABLE todo ADD COLUMN created_at TIMESTAMP;
UPDATE todo SET created_at = updated_at WHERE created_at IS NULL AND updated_at IS NOT NULL;
UPDATE todo SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;

ALTER TABLE failure_reason ADD COLUMN created_at TIMESTAMP;
UPDATE failure_reason SET created_at = updated_at WHERE created_at IS NULL AND updated_at IS NOT NULL;
UPDATE failure_reason SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;

ALTER TABLE todo ALTER COLUMN is_pinned SET DEFAULT false;
UPDATE todo SET is_pinned = false WHERE is_pinned IS NULL;

ALTER TABLE todo ADD COLUMN parent_id BIGINT REFERENCES todo(id);
