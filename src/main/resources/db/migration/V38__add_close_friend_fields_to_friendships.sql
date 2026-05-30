ALTER TABLE friendships
    ADD COLUMN IF NOT EXISTS is_close_friend BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE friendships
    ADD COLUMN IF NOT EXISTS close_friend_note VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_friendships_user_close_active
    ON friendships (user_id, is_close_friend, created_at DESC)
    WHERE deleted_at IS NULL;
