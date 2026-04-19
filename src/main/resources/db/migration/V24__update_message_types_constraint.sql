-- Fix: Drop old message_type constraint and add support for 'call_log' and 'audio'
ALTER TABLE messages DROP CONSTRAINT IF EXISTS messages_message_type_check;
ALTER TABLE messages ADD CONSTRAINT messages_message_type_check 
    CHECK (message_type IN ('text', 'image', 'video', 'file', 'audio', 'call_log'));

-- Also update reply_to_type for consistency
ALTER TABLE messages DROP CONSTRAINT IF EXISTS messages_reply_to_type_check;
ALTER TABLE messages ADD CONSTRAINT messages_reply_to_type_check 
    CHECK (reply_to_type IN ('text', 'image', 'video', 'file', 'audio', 'call_log'));
