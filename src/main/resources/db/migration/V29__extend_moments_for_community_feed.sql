ALTER TABLE moments
ADD COLUMN IF NOT EXISTS visibility_mode VARCHAR(20) NOT NULL DEFAULT 'FRIENDS',
ADD COLUMN IF NOT EXISTS cover_url TEXT,
ADD COLUMN IF NOT EXISTS duration_seconds INTEGER NOT NULL DEFAULT 0,
ADD COLUMN IF NOT EXISTS share_count BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS moment_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    moment_id UUID NOT NULL REFERENCES moments(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_moment_comments_moment_created_at
    ON moment_comments(moment_id, created_at DESC);

CREATE TABLE IF NOT EXISTS moment_views (
    id BIGSERIAL PRIMARY KEY,
    moment_id UUID NOT NULL REFERENCES moments(id) ON DELETE CASCADE,
    viewer_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (moment_id, viewer_id)
);

CREATE INDEX IF NOT EXISTS idx_moment_views_moment_id
    ON moment_views(moment_id);
