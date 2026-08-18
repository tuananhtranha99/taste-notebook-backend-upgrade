# Sổ Tay Khẩu Vị — Backend

Backend Java Spring Boot cho app ghi chú thích/không thích của bạn bè, có 2
tính năng dùng AI (Gemini, miễn phí):
- **Kiểm tra một món ăn mới** có hợp khẩu vị người đó không.
- **Gợi ý món yêu thích** bằng cách ghép các món/nguyên liệu trong danh sách "thích".

Việc phân loại "thích / không thích" từ câu nhập tự nhiên (VI + EN) là
**rule-based, không dùng AI** — tra trong bảng `phrase_keywords` ở DB, nên
rất nhanh và bạn có thể tự thêm từ khóa qua API bất cứ lúc nào.

## Kiến trúc

- **Spring Boot 3 / Java 17**
- **PostgreSQL** (host trên Supabase, free)
- **Gemini API** (`gemini-2.0-flash`, free tier — không cần thẻ)
- Deploy trên **Render** (free web service, chạy bằng Docker)

## 1. Tạo database trên Supabase (free)

1. Vào https://supabase.com → New Project (chọn region gần bạn, ví dụ Singapore).
2. Đợi project khởi tạo xong → vào **Project Settings → Database**.
3. Copy **Connection string** — chọn mục "Connection pooling" (Session pooler),
   dạng: `postgresql://postgres.xxxxx:[YOUR-PASSWORD]@aws-xxxxx.pooler.supabase.com:5432/postgres`
4. Từ đó bạn cần 3 giá trị cho app:
   - `DATABASE_URL` = `jdbc:postgresql://<host>:<port>/postgres` (đổi `postgresql://` thành `jdbc:postgresql://`, bỏ phần username/password ra khỏi URL)
   - `DATABASE_USERNAME` = phần username trong connection string (dạng `postgres.xxxxx`)
   - `DATABASE_PASSWORD` = mật khẩu database bạn đặt lúc tạo project

   Ví dụ nếu Supabase cho bạn:
   `postgresql://postgres.abcd1234:MyPass123@aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres`

   Thì bạn set:
   ```
   DATABASE_URL=jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres
   DATABASE_USERNAME=postgres.abcd1234
   DATABASE_PASSWORD=MyPass123
   ```

Bảng sẽ tự được tạo (Hibernate `ddl-auto: update`) và bộ từ khóa mặc định
VI/EN sẽ tự nạp vào lần chạy đầu tiên — không cần chạy script SQL thủ công.

## 2. Lấy Gemini API key (free, không cần thẻ)

1. Vào https://aistudio.google.com/apikey
2. Đăng nhập bằng Google account → "Create API key" → copy key.
3. Đây là `GEMINI_API_KEY`. Free tier: ~15 request/phút, 1 triệu token/ngày,
   dùng model `gemini-2.0-flash` (nhanh) — thoải mái cho app cá nhân.

## 3. Deploy lên Render (free)

1. Đẩy code này lên một GitHub repo của bạn.
2. Vào https://render.com → New → **Web Service** → connect tới repo đó.
3. Render sẽ tự nhận diện `Dockerfile` (đã có sẵn trong repo) — chọn:
   - **Runtime**: Docker
   - **Plan**: Free
4. Vào tab **Environment** của service, thêm các biến:
   - `DATABASE_URL`
   - `DATABASE_USERNAME`
   - `DATABASE_PASSWORD`
   - `GEMINI_API_KEY`
5. Deploy. Sau khi build xong, Render cho bạn 1 URL dạng
   `https://taste-notebook-backend.onrender.com` — đây là API sống 24/7
   (dùng được từ iPhone, Android, bất kỳ đâu).

   ⚠️ Lưu ý gói free của Render: service sẽ "ngủ" sau ~15 phút không có
   request, và mất khoảng 30-60 giây để "thức dậy" ở request đầu tiên sau đó.
   Đây là giới hạn bình thường của gói free, không phải lỗi.

## API chính

```
GET    /api/health

GET    /api/friends
POST   /api/friends                         {"name": "..."}
DELETE /api/friends/{id}                    (xóa luôn toàn bộ preference entries của người này)

GET    /api/friends/{id}/entries                     (tất cả category)
GET    /api/friends/{id}/entries?category=FOOD       (lọc theo category: FOOD | GIFT | ACTIVITY | OTHER)
POST   /api/friends/{id}/entries            {"text": "không thích rau mùi", "category": "FOOD"}
                                             -> category mặc định "FOOD" nếu bỏ trống
                                             -> nếu item đã tồn tại (cùng friend/category/sentiment,
                                                không phân biệt hoa thường/dấu), không insert thêm,
                                                trả về entry cũ với "duplicate": true, cập nhật intensity
                                                nếu mức độ mới khác mức cũ
DELETE /api/entries/{id}

POST   /api/friends/{id}/check              {"dish": "Sữa chua kiều mạch thêm nha đam"}
                                             -> chỉ so với các entry category=FOOD
POST   /api/friends/{id}/suggest
                                             -> chỉ ghép từ các entry category=FOOD, sentiment=LIKE

GET    /api/keywords
POST   /api/keywords    {"phrase":"kết đôi", "language":"VI", "sentiment":"LIKE", "priority": 10, "intensity": 3}
DELETE /api/keywords/{id}
```

`intensity` là số sao 1–5 thể hiện mức độ thích/không thích, được suy ra tự
động từ `intensity` của phrase khớp trong `phrase_keywords` (ví dụ "ghét" =
4 sao, "dị ứng"/"cực ghét" = 5 sao). Chỉnh bảng này qua `/api/keywords` hoặc
tab Setting trên frontend.

## Chạy thử local (tùy chọn, không bắt buộc)

Nếu muốn test trước khi deploy, bạn có thể trỏ `DATABASE_URL` về chính
Supabase luôn (không cần Postgres cài local):

```bash
export DATABASE_URL=jdbc:postgresql://<host>:5432/postgres
export DATABASE_USERNAME=postgres.xxxxx
export DATABASE_PASSWORD=xxxxx
export GEMINI_API_KEY=xxxxx
mvn spring-boot:run
```
