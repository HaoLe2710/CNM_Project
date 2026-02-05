-- Enable extensions
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =========================
-- USER
-- =========================

CREATE TABLE user_profiles (
                               user_id UUID PRIMARY KEY,
                               display_name TEXT,
                               first_name TEXT,
                               last_name TEXT,
                               avatar_url TEXT,
                               username TEXT UNIQUE,
                               invite_link TEXT UNIQUE,
                               qr_code_url TEXT,
                               bio TEXT,
                               phone TEXT,
                               banned_until TIMESTAMPTZ,
                               created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                               updated_at TIMESTAMPTZ
);

CREATE TABLE friend_requests (
                                 id BIGSERIAL PRIMARY KEY,
                                 sender_id UUID NOT NULL,
                                 receiver_id UUID NOT NULL,
                                 status TEXT NOT NULL CHECK (status IN ('pending','accepted','rejected')),
                                 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                                 updated_at TIMESTAMPTZ
);

CREATE TABLE friendships (
                             id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                             user_id UUID NOT NULL,
                             friend_id UUID NOT NULL,
                             created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                             updated_at TIMESTAMPTZ,
                             deleted_at TIMESTAMPTZ
);

CREATE TABLE user_blocks (
                             id BIGSERIAL PRIMARY KEY,
                             blocker_id UUID NOT NULL,
                             blocked_id UUID NOT NULL,
                             reason TEXT,
                             created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                             deleted_at TIMESTAMPTZ
);

CREATE TABLE user_devices (
                              id BIGSERIAL PRIMARY KEY,
                              user_id UUID NOT NULL UNIQUE,
                              device_id TEXT,
                              push_token TEXT,
                              platform TEXT CHECK (platform IN ('ios','android','web')),
                              device_name TEXT,
                              last_seen_at TIMESTAMPTZ DEFAULT now(),
                              created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_settings (
                               user_id UUID NOT NULL,
                               key TEXT NOT NULL,
                               value JSONB,
                               updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                               PRIMARY KEY (user_id, key)
);

-- =========================
-- ROOM
-- =========================

CREATE TABLE conversations (
                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               name TEXT,
                               type TEXT NOT NULL CHECK (type IN ('private','group')),
                               creator_id UUID NOT NULL,
                               created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                               updated_at TIMESTAMPTZ,
                               deleted_at TIMESTAMPTZ
);

CREATE TABLE conversation_members (
                                      conversation_id UUID NOT NULL,
                                      user_id UUID NOT NULL,
                                      role TEXT NOT NULL CHECK (role IN ('owner','admin','member','guest')),
                                      joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                                      PRIMARY KEY (conversation_id, user_id)
);

-- =========================
-- MESSAGE
-- =========================

CREATE TABLE messages (
                          id BIGSERIAL PRIMARY KEY,
                          conversation_id UUID NOT NULL,
                          sender_id UUID NOT NULL,
                          content TEXT,
                          message_type TEXT NOT NULL CHECK (message_type IN ('text','image','video','file')),
                          reply_to BIGINT,
                          created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                          updated_at TIMESTAMPTZ,
                          deleted_at TIMESTAMPTZ
);

CREATE TABLE message_status (
                                message_id BIGINT NOT NULL,
                                user_id UUID NOT NULL,
                                status TEXT NOT NULL CHECK (status IN ('sent','delivered','seen')),
                                updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                                PRIMARY KEY (message_id, user_id)
);

CREATE TABLE message_attachments (
                                     id BIGSERIAL PRIMARY KEY,
                                     message_id BIGINT NOT NULL,
                                     file_url TEXT NOT NULL,
                                     file_type TEXT,
                                     file_size BIGINT,
                                     created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =========================
-- SOCIAL
-- =========================

CREATE TABLE posts (
                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       user_id UUID NOT NULL,
                       image_url TEXT NOT NULL,
                       caption TEXT,
                       created_at TIMESTAMPTZ DEFAULT now(),
                       updated_at TIMESTAMPTZ,
                       deleted_at TIMESTAMPTZ
);

CREATE TABLE post_likes (
                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            post_id UUID NOT NULL,
                            user_id UUID NOT NULL,
                            created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE post_comments (
                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               post_id UUID NOT NULL,
                               user_id UUID NOT NULL,
                               content TEXT NOT NULL,
                               parent_comment_id UUID,
                               created_at TIMESTAMPTZ DEFAULT now(),
                               updated_at TIMESTAMPTZ,
                               deleted_at TIMESTAMPTZ
);

CREATE TABLE moments (
                         id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                         user_id UUID NOT NULL,
                         caption TEXT,
                         media_url TEXT NOT NULL,
                         media_type TEXT NOT NULL CHECK (media_type IN ('image','video')),
                         created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                         updated_at TIMESTAMPTZ,
                         deleted_at TIMESTAMPTZ
);

CREATE TABLE moment_reactions (
                                  id BIGSERIAL PRIMARY KEY,
                                  moment_id UUID NOT NULL,
                                  user_id UUID NOT NULL,
                                  reaction_type TEXT NOT NULL CHECK (reaction_type IN ('like','love','wow','haha')),
                                  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE moment_tags (
                             id BIGSERIAL PRIMARY KEY,
                             moment_id UUID NOT NULL,
                             tagged_user_id UUID NOT NULL
);

-- =========================
-- CALL
-- =========================

CREATE TABLE calls (
                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       caller_id UUID NOT NULL,
                       callee_id UUID NOT NULL,
                       channel TEXT NOT NULL,
                       status TEXT NOT NULL CHECK (
                           status IN ('calling','ringing','accepted','rejected','cancelled','busy','ended','missed')
                           ),
                       type TEXT CHECK (type IN ('voice','video')),
                       started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                       ended_at TIMESTAMPTZ,
                       created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =========================
-- SYSTEM
-- =========================

CREATE TABLE activity_logs (
                               id BIGSERIAL PRIMARY KEY,
                               user_id UUID,
                               action TEXT NOT NULL,
                               metadata JSONB,
                               created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE reports (
                         id BIGSERIAL PRIMARY KEY,
                         reporter_id UUID NOT NULL,
                         target_type TEXT NOT NULL CHECK (target_type IN ('user','message','moment')),
                         target_id TEXT NOT NULL,
                         reason TEXT,
                         ai_analysis JSONB,
                         action_taken TEXT,
                         created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                         resolved_at TIMESTAMPTZ
);

-- =========================
-- AI
-- =========================

CREATE TABLE ai_messages (
                             id BIGSERIAL PRIMARY KEY,
                             user_id UUID NOT NULL,
                             role TEXT NOT NULL CHECK (role IN ('user','assistant')),
                             content TEXT NOT NULL,
                             created_at TIMESTAMPTZ DEFAULT now()
);

-- =========================
-- INDEXES (critical for chat)
-- =========================

CREATE INDEX idx_messages_conversation_created
    ON messages (conversation_id, created_at DESC);

CREATE INDEX idx_message_status_user
    ON message_status (user_id, status);

CREATE INDEX idx_conversation_members_user
    ON conversation_members (user_id);

CREATE INDEX idx_friend_requests_receiver
    ON friend_requests (receiver_id, status);
