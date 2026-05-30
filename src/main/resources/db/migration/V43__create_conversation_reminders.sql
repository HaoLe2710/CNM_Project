CREATE TABLE IF NOT EXISTS conversation_reminders (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,
    created_by UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NULL,
    remind_at TIMESTAMP NOT NULL,
    timezone VARCHAR(80) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'SCHEDULED',
    recurrence_rule TEXT NULL,
    due_notified_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    cancelled_at TIMESTAMP NULL,
    deleted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS conversation_reminder_participants (
    id UUID PRIMARY KEY,
    reminder_id UUID NOT NULL,
    user_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    read_at TIMESTAMP NULL,
    acknowledged_at TIMESTAMP NULL,
    dismissed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_reminder_participant UNIQUE (reminder_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_conversation_reminders_conversation_time
    ON conversation_reminders (conversation_id, remind_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_conversation_reminders_due
    ON conversation_reminders (status, remind_at)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_reminder_participants_user
    ON conversation_reminder_participants (user_id, status);

CREATE INDEX IF NOT EXISTS idx_reminder_participants_reminder
    ON conversation_reminder_participants (reminder_id);
