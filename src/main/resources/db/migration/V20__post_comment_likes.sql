CREATE TABLE post_comment_likes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    comment_id UUID NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_post_comment_likes_comment_id
    ON post_comment_likes (comment_id);

CREATE INDEX idx_post_comment_likes_comment_user
    ON post_comment_likes (comment_id, user_id);
