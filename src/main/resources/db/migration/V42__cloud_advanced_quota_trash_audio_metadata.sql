ALTER TABLE cloud_files
    ADD COLUMN IF NOT EXISTS duration_ms BIGINT NULL;

ALTER TABLE cloud_files
    ADD COLUMN IF NOT EXISTS waveform TEXT NULL;

ALTER TABLE cloud_files
    ADD COLUMN IF NOT EXISTS audio_format VARCHAR(32) NULL;

CREATE INDEX IF NOT EXISTS idx_cloud_files_owner_deleted_created
    ON cloud_files (owner_id, deleted_at, created_at DESC)
    WHERE deleted_at IS NOT NULL;
