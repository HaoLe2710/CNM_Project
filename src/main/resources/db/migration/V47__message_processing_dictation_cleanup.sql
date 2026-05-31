ALTER TABLE message_processing_jobs
    ADD COLUMN IF NOT EXISTS input_cleanup_at TIMESTAMP NULL;

ALTER TABLE message_processing_jobs
    ADD COLUMN IF NOT EXISTS input_cleaned_at TIMESTAMP NULL;

ALTER TABLE message_processing_jobs
    ADD COLUMN IF NOT EXISTS input_cleanup_error TEXT NULL;

CREATE INDEX IF NOT EXISTS idx_message_processing_jobs_cleanup_due
    ON message_processing_jobs (job_scope, input_cleanup_at, created_at)
    WHERE input_cleanup_at IS NOT NULL
      AND input_cleaned_at IS NULL;
