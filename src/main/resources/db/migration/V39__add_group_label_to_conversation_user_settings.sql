ALTER TABLE conversation_user_settings
ADD COLUMN IF NOT EXISTS group_label VARCHAR(50) NULL;

CREATE INDEX IF NOT EXISTS idx_conversation_user_settings_user_group_label
ON conversation_user_settings(user_id, group_label)
WHERE group_label IS NOT NULL;
