CREATE TABLE IF NOT EXISTS polls (
    id uuid PRIMARY KEY,
    conversation_id uuid NOT NULL REFERENCES conversations(id),
    creator_id uuid NOT NULL REFERENCES user_profiles(user_id),
    question varchar(255) NOT NULL,
    multiple_choice boolean NOT NULL DEFAULT false,
    anonymous boolean NOT NULL DEFAULT false,
    status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    expires_at timestamptz,
    closed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz,
    deleted_at timestamptz
);

CREATE TABLE IF NOT EXISTS poll_options (
    id uuid PRIMARY KEY,
    poll_id uuid NOT NULL REFERENCES polls(id) ON DELETE CASCADE,
    text varchar(255) NOT NULL,
    position integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz,
    deleted_at timestamptz
);

CREATE TABLE IF NOT EXISTS poll_votes (
    id uuid PRIMARY KEY,
    poll_id uuid NOT NULL REFERENCES polls(id) ON DELETE CASCADE,
    option_id uuid NOT NULL REFERENCES poll_options(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES user_profiles(user_id),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_poll_votes_poll_option_user UNIQUE (poll_id, option_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_polls_conversation_created
    ON polls (conversation_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_poll_options_poll_position
    ON poll_options (poll_id, position)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_poll_votes_poll
    ON poll_votes (poll_id);

CREATE INDEX IF NOT EXISTS idx_poll_votes_user
    ON poll_votes (user_id);
