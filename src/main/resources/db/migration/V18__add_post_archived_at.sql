ALTER TABLE posts
    ADD COLUMN archived_at TIMESTAMPTZ;

CREATE INDEX idx_posts_user_created_active
    ON posts (user_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_posts_user_archived_active
    ON posts (user_id, archived_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_moments_video_active
    ON moments (media_type, created_at DESC)
    WHERE deleted_at IS NULL;
