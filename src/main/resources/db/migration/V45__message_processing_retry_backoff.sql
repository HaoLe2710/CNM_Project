ALTER TABLE message_processing_jobs
    ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMP NULL;

ALTER TABLE message_processing_jobs
    ADD COLUMN IF NOT EXISTS input_language VARCHAR(30) NULL;

ALTER TABLE message_processing_jobs
    ADD COLUMN IF NOT EXISTS input_voice VARCHAR(80) NULL;

UPDATE message_processing_jobs
SET next_attempt_at = created_at
WHERE next_attempt_at IS NULL
  AND status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_message_processing_jobs_status_next_attempt
    ON message_processing_jobs (status, next_attempt_at, created_at);
