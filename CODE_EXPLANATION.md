# TÀI LIỆU GIẢI THÍCH CHI TIẾT CÁC FILE CODE MỚI TRONG DỰ ÁN SOCKET-PROJECT

Dự án đã được nâng cấp từ một Socket Chat Server cơ bản thành hệ thống **SecureChat Multi-Protocol v3.0** hoàn chỉnh, hỗ trợ đồng thời TCP Client, Web Client (WebSocket/HTTP), mã hóa AES-256-GCM, bảo mật mật khẩu PBKDF2 và lưu trữ cơ sở dữ liệu SQLite.

Dưới đây là giải thích chi tiết chức năng, cấu trúc và luồng xử lý của từng file mới được thêm vào hoặc nâng cấp chính trong hệ thống:

---

## 1. `CryptoUtils.java` (Bộ mã hóa dữ liệu AES-256-GCM)
* **Mục đích**: Cung cấp các tiện ích mã hóa và giải mã dữ liệu theo chuẩn cao cấp **AES-256-GCM** (Galois/Counter Mode).
* **Các thành phần chính**:
  * `generateKey()`: Tạo ngẫu nhiên khoá bí mật AES 256-bit bằng `KeyGenerator`.
  * `encrypt(String plainText, SecretKey key)`: Mã hóa chuỗi văn bản thành dữ liệu byte mã hóa bao gồm **IV (Initialization Vector 12-byte)** ngẫu nhiên ghép với ciphertext.
  * `decrypt(byte[] cipherTextWithIv, SecretKey key)`: Tách 12 byte IV đầu tiên và thực hiện giải mã + xác thực toàn vẹn dữ liệu (Authentication Tag).
  * `keyToString(SecretKey key)` / `stringToKey(String keyStr)`: Chuyển đổi khoá AES sang dạng mã hóa Base64 để gửi qua mạng trong quá trình bắt tay (Handshake).

---

## 2. `PasswordUtils.java` (Bộ băm & Xác thực mật khẩu PBKDF2)
* **Mục đích**: Bảo vệ mật khẩu người dùng trước các cuộc tấn công Rainbow Table / Brute-force bằng thuật toán **PBKDF2WithHmacSHA256**.
* **Các thành phần chính**:
  * `hashPassword(String plainPassword)`:
    * Tạo ngẫu nhiên chuỗi **Salt** (16-byte).
    * Áp dụng **10,000 vòng lặp (iterations)** băm băm mật khẩu ra khoá 256-bit.
    * Trả về chuỗi lưu trữ định dạng `salt:hash` dưới dạng Base64.
  * `verifyPassword(String plainPassword, String storedHash)`:
    * Tách Salt từ chuỗi `storedHash`.
    * Băm mật khẩu người dùng nhập với Salt đó và so sánh kết quả thời gian thực (`MessageDigest.isEqual`) để tránh tấn công Timing Attack.

---

## 3. `DatabaseManager.java` (Quản lý Cơ sở Dữ liệu SQLite)
* **Mục đích**: Lưu trữ bền vững dữ liệu tài khoản người dùng, lịch sử tin nhắn và nhật ký truy cập.
* **Các thành phần chính**:
  * **Singleton Pattern**: Chỉ duy nhất 1 instance quản lý kết nối SQLite (`chat_server.db`).
  * `initDatabase()`: Tự động khởi tạo cấu trúc bảng:
    * `users`: Lưu `username`, `password_hash`, `created_at`, `last_login`.
    * `messages`: Lưu lịch sử chat mã hóa/plain (`sender`, `content`, `type`, `target`, `timestamp`).
  * `registerUser(username, password)`: Kiểm tra trùng lặp và lưu tài khoản mới đã được băm mật khẩu.
  * `authenticateUser(username, password)`: Xác thực tài khoản đăng nhập.
  * `saveMessage()` / `getRecentMessages()`: Lưu trữ và lấy lại 50 tin nhắn gần nhất cho người dùng mới đăng nhập.

---

## 4. `WebSocketHandler.java` (Bộ xử lý giao thức WebSocket RFC 6455)
* **Mục đích**: Cho phép trình duyệt Web (Chrome, Edge, Firefox) kết nối trực tiếp đến Server qua WebSocket port `5001`.
* **Các thành phần chính**:
  * `performHandshake()`:
    * Đọc HTTP Header gửi từ Web Client byte-by-byte để tránh consume nhầm gói tin payload.
    * Trích xuất header `Sec-WebSocket-Key`.
    * Ghép với chuỗi **Magic String RFC 6455** (`258EAFA5-E914-47DA-95CA-C5AB0DC85B11`), băm SHA-1 và mã hóa Base64 để tạo header `Sec-WebSocket-Accept`.
    * Trả về HTTP `101 Switching Protocols`.
  * `readFrame()` / `sendFrame()`: Giải mã các frame dữ liệu WebSocket (xử lý Bit Masking từ Browser gửi lên và đóng gói Frame Unmasked từ Server gửi về).
  * `handleMessage()`: Nhận dữ liệu JSON từ Web Client (`type: auth`, `chat`, `command`) và định tuyến đến `DatabaseManager` hoặc `Server.broadcast`.

---

## 5. `web/` (Giao diện Web Client Modern App)
Bộ 3 file giao diện Web Chat cao cấp thiết kế theo phong cách Dark Mode Glassmorphic:
* **`web/index.html`**:
  * Cấu trúc HTML5 Semantic.
  * Màn hình Đăng nhập / Đăng ký dạng Card nổi hiệu ứng mờ ảo (Glassmorphism).
  * Giao diện Chat chính gồm: Sidebar phòng chat, danh sách Online, khung tin nhắn và ô nhập tin nhắn kèm nút gửi/gõ phím Enter.
* **`web/style.css`**:
  * Hệ thống biến CSS Custom Properties (Color Palette HSL sang trọng).
  * Hiệu ứng chuyển động mượt mà (Micro-animations, Keyframe animations).
  * Responsive hoàn toàn trên màn hình Desktop và Mobile.
* **`web/app.js`**:
  * Khởi tạo kết nối `WebSocket` tới `ws://localhost:5001`.
  * Xử lý trạng thái UI (Chuyển đổi giữa Màn hình Auth và Màn hình Chat).
  * Định dạng thời gian, tự động cuộn (Auto-scroll) khung tin nhắn, rendering tin nhắn hệ thống, tin nhắn riêng (PM) và chat chung.

---

## 6. Unit Tests & Docker Configuration
* **`CryptoUtilsTest.java`**: Kiểm thử tự động mã hóa/giải mã AES-256-GCM, kiểm tra tính đúng đắn và xử lý ngoại lệ khoá sai.
* **`PasswordUtilsTest.java`**: Kiểm thử băm mật khẩu PBKDF2 và xác thực đúng/sai mật khẩu.
* **`Dockerfile`**: Cấu hình đóng gói ứng dụng thành Docker Container (Multi-stage build từ JDK 21 Alpine, bao gồm đầy đủ các thư viện `sqlite-jdbc.jar`, `slf4j-api.jar`, `slf4j-nop.jar`).
* **`docker-compose.yml`**: Định nghĩa Service chạy container đồng thời mở 3 cổng `5000` (TCP), `5001` (WebSocket), `5002` (HTTP Web).

---

## 🛠 Hướng dẫn vận hành nhanh:
1. **Chạy Server**:
   ```powershell
   java -cp ".;sqlite-jdbc.jar;slf4j-api.jar;slf4j-nop.jar" Server
   ```
2. **Truy cập Web UI**: Mở trình duyệt vào `http://localhost:5002` để đăng ký / đăng nhập và trải nghiệm chat real-time.
