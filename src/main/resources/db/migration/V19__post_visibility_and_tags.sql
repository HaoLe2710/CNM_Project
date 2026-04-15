ALTER TABLE posts
    ADD COLUMN visibility_mode TEXT NOT NULL DEFAULT 'ALL_FRIENDS';

CREATE TABLE post_tags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL,
    tagged_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE post_visibility_grants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL,
    viewer_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_post_tags_post_id
    ON post_tags (post_id);

CREATE INDEX idx_post_visibility_grants_post_id
    ON post_visibility_grants (post_id);

CREATE INDEX idx_post_visibility_grants_viewer_user_id
    ON post_visibility_grants (viewer_user_id);
