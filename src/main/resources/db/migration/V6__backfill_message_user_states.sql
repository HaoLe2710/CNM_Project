INSERT INTO message_user_states (
    message_id,
    user_id,
    seen_at,
    created_at,
    updated_at
)
SELECT
    ms.message_id,
    ms.user_id,
    CASE
        WHEN ms.status = 'seen' THEN ms.updated_at
        ELSE NULL
    END AS seen_at,
    COALESCE(ms.updated_at, now()) AS created_at,
    COALESCE(ms.updated_at, now()) AS updated_at
FROM message_status ms
ON CONFLICT (message_id, user_id) DO UPDATE
SET seen_at = COALESCE(message_user_states.seen_at, EXCLUDED.seen_at);

INSERT INTO message_user_states (
    message_id,
    user_id,
    hidden_at,
    created_at,
    updated_at
)
SELECT
    mhu.message_id,
    mhu.user_id,
    mhu.hidden_at,
    COALESCE(mhu.hidden_at, now()) AS created_at,
    COALESCE(mhu.hidden_at, now()) AS updated_at
FROM message_hidden_users mhu
ON CONFLICT (message_id, user_id) DO UPDATE
SET hidden_at = COALESCE(message_user_states.hidden_at, EXCLUDED.hidden_at);
