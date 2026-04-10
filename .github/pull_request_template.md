# 🚀 Pull Request: Call Feature Integration & Security Hardening

## 📌 Summary
Bản cập nhật này triển khai nền tảng cho tính năng **gọi điện (Call)** và tích hợp **Firebase Cloud Messaging (FCM)** để gửi thông báo thời gian thực. Đồng thời, thực hiện các bước bảo mật quan trọng cho mã nguồn.

### Các thành phần chính:
- **Call Module**: Khởi tạo và quản lý thực thể cuộc gọi (Controller, Service, Entity).
- **FCM Integration**: Tích hợp Firebase Admin SDK để gửi Push Notification.
- **Database Migration**: Cập nhật schema để lưu trữ `fcm_token` của người dùng.
- **Security & Git Policy**: Chặn file cấu hình nhạy cảm và cập nhật phân quyền truy cập.

---

## 🎯 Purpose / Motivation
- **Real-time Signaling**: Cung cấp API khởi tạo cuộc gọi, làm tiền đề cho việc tích hợp WebRTC (Voice/Video) trong tương lai.
- **User Engagement**: Đảm bảo người nhận nhận được thông báo cuộc gọi đến ngay cả khi ứng dụng đang chạy ngầm.
- **Security Compliance**: Loại bỏ hoàn toàn file credentials (`firebase-service-account.json`) khỏi lịch sử Git để tránh rò rỉ bảo mật.

---

## 🔧 Changes

### 1. Backend - Call Module & Notification
- [x] **`CallController.java`**: Endpoint xử lý yêu cầu khởi tạo cuộc gọi từ Client.
- [x] **`Call.java`**: Entity định nghĩa cấu trúc dữ liệu cho một cuộc gọi.
- [x] **`CallService.java`**: Logic nghiệp vụ điều phối thông tin cuộc gọi.
- [x] **`FCMService.java`**: Xử lý việc gửi thông báo đẩy đến thiết bị người nhận qua FCM.

### 2. Database & Security Updates
- [x] **`V2__add_fcm_token_to_user_profiles.sql`**: Script Migration bổ sung cột `fcm_token` vào bảng profile người dùng.
- [x] **`SecurityConfig.java`**: Cập nhật cấu hình bảo mật Spring Security để phân quyền cho các endpoint mới.
- [x] **`.gitignore`**: Cập nhật quy tắc để chặn file cấu hình Firebase.
- [x] **Git Tracking**: Thực hiện `git rm --cached` để ngừng theo dõi file nhạy cảm.

---

## 🛡️ Security Note
> [!IMPORTANT]
> File `src/main/resources/firebase-service-account.json` đã được đưa vào danh sách chặn và xóa khỏi bộ nhớ đệm của Git.
> **Lưu ý:** Các thành viên trong nhóm cần tự cấu hình file này tại máy cá nhân để chạy tính năng thông báo. Tuyệt đối không đẩy file này lên Repo.

---

## 🧪 Testing Status
- [ ] **Unit Tests**: Chưa thực hiện.
- [ ] **Integration Tests**: Chưa thực hiện.
- [ ] **End-to-End**: Chưa thực hiện.

**Ghi chú:** Bản commit này tập trung vào cấu trúc code và tích hợp thư viện. Việc kiểm thử tính năng sẽ được tiến hành sau khi hoàn thiện môi trường Firebase Client.

---

## 👨‍💻 Author
**PhamTangHoang_22691881**