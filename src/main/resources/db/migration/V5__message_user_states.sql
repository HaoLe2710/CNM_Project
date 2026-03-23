CREATE TABLE message_user_states (
    id BIGSERIAL PRIMARY KEY,
    message_id BIGINT NOT NULL REFERENCES messages (id),
    user_id UUID NOT NULL,
    seen_at TIMESTAMPTZ,
    hidden_at TIMESTAMPTZ,
    deleted_for_me_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_message_user_states_message_user UNIQUE (message_id, user_id)
);

CREATE INDEX idx_message_user_states_user_message
    ON message_user_states (user_id, message_id);
