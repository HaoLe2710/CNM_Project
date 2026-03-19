# 🚀 Pull Request: AI-Powered Chat Integration & Smart Summarization

## 📌 Summary
Bản cập nhật này tập trung vào việc tích hợp trí tuệ nhân tạo (AI) để tối ưu hóa trải nghiệm người dùng trong hệ thống chat. Trọng tâm là tính năng tóm tắt tin nhắn thông minh và xử lý hội thoại tự động.

### Các thành phần chính:
- **Tích hợp Spring AI**: Triển khai `AiService` và `AiSummaryService` kết nối với OpenRouter (ChatGPT model).
- **Tính năng Smart Summarization**: Tự động đưa ra quyết định hiển thị tin nhắn trực tiếp (khi có $\le 5$ tin) hoặc gọi AI tóm tắt (khi có $> 5$ tin).
- **Message Processing Pipeline**: Chuẩn hóa dữ liệu qua `MessageProcessor`, gộp nhóm tin nhắn theo người gửi để tối ưu hóa context cho AI.
- **Repository Optimization**: Cập nhật `AiMessageRepository` với các truy vấn JPQL hỗ trợ xử lý trạng thái tin nhắn (`DELIVERED`, `SEEN`) và phân biệt hoa/thường (Case-insensitive).

---

## 🎯 Purpose / Motivation
Tại sao chúng ta cần những thay đổi này?

1. **Nâng cao trải nghiệm người dùng (UX)**: Giúp người dùng nhanh chóng nắm bắt nội dung hội thoại khi quay lại một nhóm chat có quá nhiều tin nhắn mới.
2. **Kiến trúc linh hoạt**: Tách biệt logic xử lý dữ liệu thô (`MessageProcessor`) và logic AI (`AiSummaryService`) giúp dễ dàng bảo trì và thay đổi Model AI sau này.
3. **Quản lý Token**: Việc gộp tin nhắn theo người gửi giúp giảm số lượng Token gửi lên AI, từ đó tiết kiệm chi phí và tăng tốc độ phản hồi.

---

## 🔧 Changes
Danh sách các thay đổi chi tiết:

### 1. Backend Logic & Services
- [x] **`AiMessageRepository.java`**: Thêm `@Query` đếm tin nhắn chưa đọc và lấy tin nhắn gần nhất dựa trên `conversationId` và `userId`.
- [x] **`ChatSummaryService.java`**: Triển khai logic điều phối (Orchestration) luồng dữ liệu.
- [x] **`AiSummaryService.java`**: Xây dựng System Prompt chuyên sâu để AI tóm tắt tự nhiên, gần gũi.
- [x] **`MessageProcessor.java`**: Chuyển đổi dữ liệu sang `MessageAiDto` và xử lý logic `groupBySender`.

### 2. API & Configuration
- [x] **`ChatController.java`**: Thêm endpoint `GET /api/summary/{conversationId}`.
- [x] **`SecurityConfig.java`**: Cấu hình PermitAll cho các endpoint `/api/**` phục vụ giai đoạn phát triển và test.
- [x] **`AiConfig.java`**: Quản lý cấu hình ChatClient và API Key.

---

## 🧪 Testing
Các phương pháp đã dùng để kiểm chứng:

- **Manual API Testing**: Đã test thành công trên Postman với các bộ dữ liệu UUID giả lập (A và B).
- **SQL Verification**: Kiểm tra trực tiếp trên PostgreSQL để đảm bảo các ràng buộc Check Constraint (`private`/`group`) hoạt động đúng.
- **Prompt Refinement**: Đã tinh chỉnh System Prompt để tránh nội dung tóm tắt bị "cứng nhắc".
- **Lưu ý**: Chưa triển khai Unit Test chính thức, sẽ bổ sung trong các PR tiếp theo.

---

## 📸 Expected Results
- **Trường hợp $\le 5$ tin nhắn**: Trả về tin nhắn cuối cùng kèm tên người gửi.
    - *Ví dụ:* `Nguyễn Văn A: Đợi xíu qua đón nha`
- **Trường hợp $> 5$ tin nhắn**: Trả về đoạn tóm tắt thông minh từ AI.
    - *Ví dụ:* `Nguyễn Văn A đang giục bạn đi ăn và báo là đã tới nơi đón bạn rồi đó.`

---
*Created by Hoang - CNM Project Team*