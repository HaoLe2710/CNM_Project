-- V23: Tạo bảng quản lý Cuộc gọi Nhóm (Group Call)
-- Tách biệt hoàn toàn với bảng calls (cuộc gọi 1-1)

-- 1. Bảng quản lý thông tin chung của cuộc gọi nhóm
CREATE TABLE group_calls (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,
    initiator_id UUID NOT NULL,
    channel VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,           -- RINGING, ONGOING, ENDED
    type VARCHAR(10) NOT NULL DEFAULT 'VIDEO', -- VIDEO hoặc VOICE
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    ended_at TIMESTAMP WITHOUT TIME ZONE
);

-- 2. Bảng quản lý trạng thái của từng thành viên trong cuộc gọi
-- Chiến lược: MỖI NGƯỜI CHỈ CÓ 1 HÀNG DUY NHẤT trong 1 cuộc gọi
-- (tránh phình CSDL khi người dùng vào/ra nhiều lần)
CREATE TABLE group_call_participants (
    id UUID PRIMARY KEY,
    group_call_id UUID NOT NULL REFERENCES group_calls(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    state VARCHAR(50) NOT NULL,            -- INVITED, JOINED, LEFT, DECLINED
    is_camera_on BOOLEAN DEFAULT FALSE,
    is_mic_on BOOLEAN DEFAULT FALSE,
    join_count INTEGER DEFAULT 0,          -- Số lần tham gia (tăng mỗi khi rejoin)
    last_joined_at TIMESTAMP WITHOUT TIME ZONE,
    total_duration BIGINT DEFAULT 0,       -- Tổng thời gian online (giây)

    -- Ràng buộc: Mỗi user chỉ có 1 hàng duy nhất trong 1 cuộc gọi
    UNIQUE (group_call_id, user_id)
);

-- Index để truy vấn nhanh cuộc gọi theo phòng chat
CREATE INDEX idx_group_calls_conversation_id ON group_calls(conversation_id);
-- Index để truy vấn nhanh trạng thái participant
CREATE INDEX idx_group_call_participants_group_call_id ON group_call_participants(group_call_id);
