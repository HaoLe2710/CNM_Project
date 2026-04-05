create table conversation_user_settings (
    id bigserial primary key,
    conversation_id uuid not null references conversations(id),
    user_id uuid not null,
    muted_at timestamptz null,
    archived_at timestamptz null,
    pinned_at timestamptz null,
    created_at timestamptz not null default now(),
    updated_at timestamptz null,
    constraint uk_conversation_user_settings_conversation_user unique (conversation_id, user_id)
);

create index idx_conversation_user_settings_user on conversation_user_settings(user_id);
create index idx_conversation_user_settings_conversation_user on conversation_user_settings(conversation_id, user_id);
