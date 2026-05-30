CREATE TABLE IF NOT EXISTS device_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    device_id VARCHAR(255) NOT NULL,
    platform VARCHAR(30) NOT NULL,
    provider VARCHAR(30) NOT NULL,
    token TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_seen_at TIMESTAMP WITHOUT TIME ZONE NULL,
    last_failed_at TIMESTAMP WITHOUT TIME ZONE NULL,
    revoked_at TIMESTAMP WITHOUT TIME ZONE NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_device_tokens_user_device_platform
        UNIQUE (user_id, device_id, platform)
);

CREATE INDEX IF NOT EXISTS idx_device_tokens_user_active
    ON device_tokens(user_id, enabled, revoked_at);

CREATE INDEX IF NOT EXISTS idx_device_tokens_user_platform
    ON device_tokens(user_id, platform);
