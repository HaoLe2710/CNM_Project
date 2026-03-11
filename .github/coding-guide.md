# 🚀 Hướng dẫn Chạy dự án & Quản lý Database

Tài liệu này hướng dẫn cách thiết lập môi trường, phát triển cấu trúc DB bằng Flyway và cách chia sẻ dữ liệu test giữa các thành viên.



## 🏃 1. Cách chạy dự án chính xác

Để đảm bảo dự án chạy không lỗi Schema, hãy tuân thủ các bước sau:

1. **Khởi động Database:**
   Chạy trực tiếp source code sau khi pull code về, Flyway sẽ trực tiếp quét file migrate database để dựng bản.


2. **Kiểm tra DB:** Đảm bảo container `chat-postgres` đang chạy (Dùng lệnh `docker ps`).

3. **Kiểm tra cấu trúc DB:** Vào Datasource thêm một data source mới cho postgresql với thông tin:
- user: `chat`
- password: `chat`
- database: `chat`
- host: `localhost`
- port: `5432`


*Lưu ý: Flyway sẽ tự động quét thư mục `src/main/resources/db/migration` để dựng bảng.*

---

## 🔄 3. Cách tạo file Migrate với JPA Buddy

Khi bạn thay đổi code (thêm field vào Entity), hãy để **JPA Buddy** tự viết SQL cho bạn nhằm tránh sai sót.

1. **Sửa Entity:** Thêm thuộc tính hoặc tạo Entity mới trong Java.
2. **Mở tab JPA Structure:** (Thường ở góc dưới bên trái IntelliJ).
3. **Chuột phải vào "Migrations":** Chọn **"Diff Changelog"** hoặc **"Flyway Migration"**.
4. **Cấu hình Diff:**
* **Source:** Java Entities.
* **Target:** DB hiện tại (PostgreSQL đang chạy trên Docker).


5. **Generate:** JPA Buddy sẽ so sánh và tạo ra một file `.sql` mới (ví dụ: `V2__add_column_x.sql`).
6. **Kiểm tra & Lưu:** File sẽ tự động được đặt vào `src/main/resources/db/migration`. Lần tới khi khởi động App, Flyway sẽ tự chạy file này.

---

## 💾 4. Backup dữ liệu để người khác có thể Test

Thông thường, chúng ta không đẩy thư mục `pg_data` của Docker lên Git vì nó rất nặng và khó quản lý phiên bản. Thay vào đó, ta sẽ sử dụng các script SQL.

### Bước A: Xuất dữ liệu (Export)

Khi bạn đã nhập dữ liệu mẫu vào máy mình và muốn chia sẻ, hãy chạy lệnh:

```bash
# Xuất toàn bộ dữ liệu ra file seed_data.sql
docker exec -t chat-postgres pg_dump -U chat -d chat --data-only --inserts > src/main/resources/db/test_data/seed_data.sql

```

*Ghi chú: Tham số `--data-only` chỉ lấy dữ liệu, không lấy cấu trúc (vì cấu trúc đã có Flyway lo).*

### Bước B: Đẩy lên Git

Thêm file `seed_data.sql` vào Git và push lên.

### Bước C: Người khác lấy về dùng (Import)

Sau khi đồng nghiệp lấy code về và chạy `docker-compose up`, họ chỉ cần chạy lệnh sau để có dữ liệu test giống hệt bạn:

```bash
cat src/main/resources/db/test_data/seed_data.sql | docker exec -i chat-postgres psql -U chat -d chat

```

---

## 💾 4. Đẩy code lên Github đúng cách

- Mỗi người tạo một branch có tên như sau: `HoTen_MSSV`
- Khi commit code lên Github, cần tạo message theo đúng chuẩn như sau: ``
- Sau khi gộp xong, tạo pull request vào nhánh `dev`.
- Khi tạo pull request, hãy tóm tắt những việc mình đã làm trong đoạn code cần push, sau đó dùng AI Gen cùng với file `.github/pull-request-template.md` để tạo nội dung chi tiết cho pull request.
- Tuyệt đối không trực tiếp push vào nhánh `main`.
- Nếu cần sửa gấp liên quan đến `entity`, `repository`, ... (nói chung là base của dự án), cần liên hệ leader (có thể cần pull gấp code vào nhánh `main` hoặc `dev` để xử lý).

## ⚠️ Lưu ý quan trọng

* **Không bao giờ sửa file Migration cũ:** Nếu file `V1` đã được push lên Git và đồng nghiệp đã chạy, bạn sửa nội dung `V1` sẽ gây lỗi `Checksum mismatch`. Hãy luôn tạo file `V2`, `V3` mới.
* **Quy tắc đặt tên:** Luôn dùng 2 dấu gạch dưới (`__`) sau ký tự phiên bản (Ví dụ: `V2__description.sql`).
* **Gitignore:** Đảm bảo file `.gitignore` đã có thư mục dữ liệu Docker (ví dụ: `postgres_data/` hoặc `pg_data/`) để không đẩy file rác lên Git.

---
