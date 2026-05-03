CREATE TABLE IF NOT EXISTS security_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    title VARCHAR(255) NOT NULL,
    detail TEXT,
    device_id VARCHAR(255),
    platform VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_security_audit_logs_user_created_at
    ON security_audit_logs(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_security_audit_logs_user_event_type
    ON security_audit_logs(user_id, event_type);
