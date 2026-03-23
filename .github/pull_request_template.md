# 🚀 Pull Request: Call Feature Integration (Voice/Video Call + FCM)

## 📌 Summary
Bản cập nhật này triển khai bước đầu tính năng **gọi điện (Call)** trong hệ thống, bao gồm việc khởi tạo cuộc gọi, xử lý logic backend và gửi thông báo đến người nhận thông qua Firebase Cloud Messaging (FCM).

Tính năng này đóng vai trò nền tảng cho việc phát triển các chức năng realtime như **Voice Call / Video Call** trong tương lai.

### Các thành phần chính:
- **Call API**: Endpoint khởi tạo cuộc gọi giữa các user.
- **Call Service**: Xử lý logic nghiệp vụ liên quan đến cuộc gọi.
- **FCM Integration**: Gửi push notification khi có cuộc gọi đến.
- **Firebase Configuration**: Kết nối với Firebase để hỗ trợ notification.

---

## 🎯 Purpose / Motivation

### Tại sao cần thay đổi này?

1. **Hỗ trợ giao tiếp realtime**
    - Cho phép user bắt đầu cuộc gọi trực tiếp trong hệ thống.

2. **Cải thiện trải nghiệm người dùng**
    - Người nhận nhận được thông báo ngay lập tức khi có cuộc gọi đến.

3. **Xây dựng nền tảng cho WebRTC**
    - Chuẩn bị cho các bước tiếp theo:
        - Signaling server
        - Video call / voice call
        - Call lifecycle (accept / reject / end)

---

## 🔧 Changes

### 1. Backend - Call Module

- [x] **`CallController.java`**
    - Cung cấp API để khởi tạo cuộc gọi
    - Nhận request từ client và chuyển xuống service xử lý

- [x] **`CallService.java`**
    - Xử lý logic chính:
        - Xác định người gọi và người nhận
        - Tạo dữ liệu cuộc gọi (nếu cần)
        - Gửi yêu cầu notification qua FCM

- [x] **`CallResponse.java`**
    - DTO trả về thông tin cuộc gọi cho client

- [x] **`InitiateCallRequest.java`**
    - DTO nhận dữ liệu từ client khi bắt đầu cuộc gọi

---

### 2. Push Notification (Firebase Cloud Messaging)

- [x] **`FCMService.java`**
    - Thực hiện gửi push notification đến thiết bị người nhận
    - Nội dung gửi bao gồm:
        - Thông tin người gọi
        - Loại cuộc gọi (voice/video)
        - Metadata phục vụ signaling

- [x] **`FirebaseConfig.java`**
    - Cấu hình Firebase Admin SDK
    - Load credentials từ file:
      ```
      firebase-service-account.json
      ```

---

### 3. User Module (Related Updates)

- [x] **`UserController.java`**
    - Có điều chỉnh để hỗ trợ các API liên quan đến call (nếu cần)

- [x] **`UserService.java`**
    - Hỗ trợ truy xuất thông tin user phục vụ cho việc gọi

- [x] **`UserProfile.java`**
    - Có thể bổ sung các field:
        - FCM Token (để nhận notification)
        - Trạng thái user (online/offline)

---

### 4. Configuration & Dependencies

- [x] **`pom.xml`**
    - Thêm dependency cho Firebase Admin SDK

- [x] **`firebase-service-account.json`**
    - File credentials để xác thực với Firebase

---

## 🧪 Testing

### Trạng thái hiện tại:
- ❗ **Chưa thực hiện testing chính thức**


## 👨‍💻 Author
**Hoàng - CNM Project Team**