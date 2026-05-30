ALTER TABLE messages DROP CONSTRAINT IF EXISTS messages_message_type_check;

ALTER TABLE messages ADD CONSTRAINT messages_message_type_check
    CHECK (message_type IN ('text', 'image', 'video', 'file', 'audio', 'call_log', 'system'));

ALTER TABLE messages DROP CONSTRAINT IF EXISTS messages_reply_to_type_check;

ALTER TABLE messages ADD CONSTRAINT messages_reply_to_type_check
    CHECK (reply_to_type IS NULL OR reply_to_type IN ('text', 'image', 'video', 'file', 'audio', 'call_log', 'system'));
