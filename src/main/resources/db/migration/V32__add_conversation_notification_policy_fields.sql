ALTER TABLE conversation_user_settings
ADD COLUMN IF NOT EXISTS muted_until TIMESTAMP NULL;

ALTER TABLE conversation_user_settings
ADD COLUMN IF NOT EXISTS last_muted_at TIMESTAMP NULL;

ALTER TABLE conversation_user_settings
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NULL;

UPDATE conversation_user_settings
SET notification_level = 'ALL'
WHERE notification_level IS NULL;

ALTER TABLE conversation_user_settings
ALTER COLUMN notification_level SET DEFAULT 'ALL';
