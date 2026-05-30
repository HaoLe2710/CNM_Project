CREATE TABLE IF NOT EXISTS conversation_member_read_state (
    conversation_id UUID NOT NULL REFERENCES conversations(id),
    user_id UUID NOT NULL,
    last_read_message_id BIGINT NULL REFERENCES messages(id),
    last_read_at TIMESTAMPTZ NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_conversation_member_read_state PRIMARY KEY (conversation_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_cmrs_user
    ON conversation_member_read_state(user_id);

CREATE INDEX IF NOT EXISTS idx_cmrs_conversation_last_read
    ON conversation_member_read_state(conversation_id, last_read_message_id);
