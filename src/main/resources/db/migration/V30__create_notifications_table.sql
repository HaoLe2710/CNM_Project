CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY,
    recipient_id UUID NOT NULL,
    actor_id UUID NULL,
    type VARCHAR(80) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT NULL,
    target_type VARCHAR(50) NULL,
    target_id UUID NULL,
    conversation_id UUID NULL,
    message_id BIGINT NULL,
    post_id UUID NULL,
    comment_id UUID NULL,
    metadata JSONB NULL,
    dedup_key VARCHAR(255) NULL,
    read_at TIMESTAMP WITHOUT TIME ZONE NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITHOUT TIME ZONE NULL,
    deleted_at TIMESTAMP WITHOUT TIME ZONE NULL
);

CREATE INDEX IF NOT EXISTS idx_notifications_recipient_created
    ON notifications(recipient_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notifications_recipient_read
    ON notifications(recipient_id, read_at);

CREATE UNIQUE INDEX IF NOT EXISTS uk_notifications_dedup_key
    ON notifications(dedup_key)
    WHERE dedup_key IS NOT NULL;
