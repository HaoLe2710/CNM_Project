-- Thêm cột fcm_token vào bảng user_profiles
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS fcm_token TEXT;
