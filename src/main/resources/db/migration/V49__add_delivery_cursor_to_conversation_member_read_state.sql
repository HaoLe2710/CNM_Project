ALTER TABLE conversation_member_read_state
    ADD COLUMN IF NOT EXISTS last_delivered_message_id BIGINT NULL REFERENCES messages(id),
    ADD COLUMN IF NOT EXISTS last_delivered_at TIMESTAMPTZ NULL;

CREATE INDEX IF NOT EXISTS idx_cmrs_conversation_last_delivered
    ON conversation_member_read_state(conversation_id, last_delivered_message_id);

