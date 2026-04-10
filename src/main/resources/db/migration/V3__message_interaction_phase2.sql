CREATE TABLE message_reactions (
    id BIGSERIAL PRIMARY KEY,
    message_id BIGINT NOT NULL,
    user_id UUID NOT NULL,
    reaction_type TEXT NOT NULL CHECK (reaction_type IN ('like', 'love', 'wow', 'haha')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_message_reactions_message_user UNIQUE (message_id, user_id)
);

CREATE TABLE message_hidden_users (
    message_id BIGINT NOT NULL,
    user_id UUID NOT NULL,
    hidden_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (message_id, user_id)
);

CREATE INDEX idx_message_reactions_message
    ON message_reactions (message_id);

CREATE INDEX idx_message_hidden_users_user
    ON message_hidden_users (user_id);
