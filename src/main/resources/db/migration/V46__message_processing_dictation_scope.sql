ALTER TABLE message_processing_jobs
    ADD COLUMN IF NOT EXISTS conversation_id UUID NULL;

ALTER TABLE message_processing_jobs
    ADD COLUMN IF NOT EXISTS job_scope VARCHAR(30) NOT NULL DEFAULT 'MESSAGE';

ALTER TABLE message_processing_jobs
    ALTER COLUMN message_id DROP NOT NULL;

UPDATE message_processing_jobs
SET job_scope = 'MESSAGE'
WHERE job_scope IS NULL;

CREATE INDEX IF NOT EXISTS idx_message_processing_jobs_scope_conversation
    ON message_processing_jobs (job_scope, conversation_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_message_processing_jobs_requested_by_scope
    ON message_processing_jobs (requested_by, job_scope, created_at DESC);
