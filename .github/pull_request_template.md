# Pull Request

## 📌 Summary
Mô tả ngắn gọn thay đổi trong PR này.

- Implement AI chat API using OpenRouter
- Add `AiService` để xử lý request tới OpenRouter ChatGPT model
- Add `ChatController` để cung cấp endpoint chat
- Add cấu hình AI trong `AiConfig`
- Update `SecurityConfig` để cho phép truy cập AI endpoint

---

## 🎯 Purpose / Motivation
Tại sao cần thay đổi này?

- Implement feature

Mục tiêu của thay đổi này là tích hợp chức năng **AI chat** vào hệ thống backend bằng cách sử dụng **OpenRouter API với ChatGPT model**.

Backend có thể gửi prompt từ client tới OpenRouter và nhận response từ AI model, từ đó hỗ trợ các chức năng như:

- AI chatbot
- AI assistant
- AI-generated responses trong hệ thống

Ngoài ra việc tách cấu hình sang `AiConfig` giúp quản lý **API key và model configuration** dễ dàng hơn.

---

## 🔧 Changes
Danh sách các thay đổi chính:

- [x] Add `AiService`
- [x] Implement OpenRouter API integration
- [x] Add `ChatController`
- [x] Add `AiConfig`
- [x] Update `SecurityConfig`
- [ ] Update tests

Modified files:

- `src/main/java/fit/iuh/cnm_project_be/ai/controller/ChatController.java`
- `src/main/java/fit/iuh/cnm_project_be/ai/service/AiService.java`
- `src/main/java/fit/iuh/cnm_project_be/config/AiConfig.java`
- `src/main/java/fit/iuh/cnm_project_be/config/SecurityConfig.java`

---

## 🧪 Testing
Các cách đã dùng để test:

- [ ] Unit tests
- [ ] Integration tests
- [ ] Manual API testing (Postman / curl)
- [ ] Verified database changes

Hiện tại **chưa thực hiện testing**, sẽ bổ sung test trong các PR tiếp theo.