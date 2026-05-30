CREATE TABLE IF NOT EXISTS message_processing_jobs (
    id UUID PRIMARY KEY,
    message_id BIGINT NOT NULL,
    attachment_id BIGINT NULL,
    job_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    provider VARCHAR(50) NULL,
    input_mime_type VARCHAR(120) NULL,
    input_storage_key TEXT NULL,
    result_text TEXT NULL,
    result_file_url TEXT NULL,
    result_storage_key TEXT NULL,
    result_mime_type VARCHAR(120) NULL,
    error_message TEXT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    requested_by UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL
);

CREATE INDEX IF NOT EXISTS idx_message_processing_jobs_message
    ON message_processing_jobs (message_id, job_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_message_processing_jobs_status
    ON message_processing_jobs (status, job_type, created_at ASC);

CREATE INDEX IF NOT EXISTS idx_message_processing_jobs_attachment
    ON message_processing_jobs (attachment_id, job_type, created_at DESC);
