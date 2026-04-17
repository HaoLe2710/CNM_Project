ALTER TABLE messages
    ADD COLUMN reply_to_sender_id UUID,
    ADD COLUMN reply_to_content_preview TEXT,
    ADD COLUMN reply_to_type TEXT CHECK (reply_to_type IN ('text', 'image', 'video', 'file', 'audio'));

ALTER TABLE message_attachments
    ADD COLUMN storage_key TEXT,
    ADD COLUMN original_file_name TEXT,
    ADD COLUMN attachment_type TEXT NOT NULL DEFAULT 'file'
        CHECK (attachment_type IN ('text', 'image', 'video', 'file', 'audio'));
