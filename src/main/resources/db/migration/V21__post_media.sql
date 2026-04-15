CREATE TABLE post_media (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL,
    media_url TEXT NOT NULL,
    media_type TEXT NOT NULL CHECK (media_type IN ('IMAGE', 'VIDEO')),
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO post_media (post_id, media_url, media_type, sort_order, created_at)
SELECT id, image_url, 'IMAGE', 0, created_at
FROM posts
WHERE image_url IS NOT NULL AND image_url <> '';

CREATE INDEX idx_post_media_post_id
    ON post_media (post_id, sort_order, created_at);
