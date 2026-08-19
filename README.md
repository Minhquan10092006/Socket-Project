<div align="center">

# 🔒 SecureChat Multi-Protocol v3.0

### *Production-Grade Encrypted Real-Time Chat System*

[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg?style=for-the-badge&logo=openjdk)](https://www.oracle.com/java/)
[![Security](https://img.shields.io/badge/Encryption-AES--256--GCM-blue.svg?style=for-the-badge&logo=letsencrypt)](https://en.wikipedia.org/wiki/Galois/Counter_Mode)
[![Auth](https://img.shields.io/badge/Password-PBKDF2%20%2B%20Salt-red.svg?style=for-the-badge&logo=1password)](https://en.wikipedia.org/wiki/PBKDF2)
[![Database](https://img.shields.io/badge/Database-SQLite-003B57.svg?style=for-the-badge&logo=sqlite)](https://www.sqlite.org/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED.svg?style=for-the-badge&logo=docker)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-MIT-green.svg?style=for-the-badge)](LICENSE)

<p align="center">
  <b>Hệ thống Chat Socket Đa Giao Thức (TCP + WebSocket) Sản Phẩm Cao Cấp</b><br>
  Tích hợp Mã hóa đầu-cuối AES-256-GCM, Xác thực PBKDF2, Lưu trữ SQLite & Giao diện Web Glassmorphism hiện đại.
</p>

[Tính Năng](#-tính-năng-nổi-bật) • [Kiến Trúc](#-kiến-trúc-hệ-thống) • [Cài Đặt & Vận Hành](#-cài-đặt--hướng-dẫn-vận-hành) • [Cấu Trúc Dự Án](#-cấu-trúc-thư-mục) • [Bảo Mật](#-bảo-mật--mã-hóa)

</div>

---

## 🌟 Tính Năng Nổi Bật

- 🔒 **Mã Hóa Cao Cấp (AES-256-GCM)**: Bảo mật toàn vẹn dữ liệu truyền qua TCP Socket bằng thuật toán mã hóa đối xứng khối hiện đại nhất với IV ngẫu nhiên 12-byte.
- 🔑 **Bảo Mật Mật Khẩu PBKDF2**: Lưu trữ mật khẩu an toàn chống tấn công Rainbow Table nhờ **PBKDF2WithHmacSHA256** ghép kèm Salt 16-byte ngẫu nhiên và 10,000 vòng băm.
- 💾 **Lưu Trữ Bền Vững (SQLite)**: Tự động quản lý cơ sở dữ liệu `chat_server.db`, lưu trữ thông tin người dùng, lịch sử chat và tự động khôi phục 50 tin nhắn gần nhất khi đăng nhập.
- 🌐 **Đa Giao Thức (TCP + WebSocket)**:
  * **TCP Port 5000**: Phục vụ các ứng dụng Java Desktop Client mã hóa end-to-end.
  * **WebSocket Port 5001**: Xử lý giao thức RFC 6455 chuẩn hóa kết nối từ trình duyệt Web.
  * **HTTP Server Port 5002**: Tự động phục vụ file tĩnh giao diện Web UI.
- 🎨 **Giao Diện Web Glassmorphism (Modern UI)**: Giao diện web phong cách Dark Mode sang trọng, mượt mà với hiệu ứng kính mờ, micro-animations và responsive 100%.
- ⚡ **Hiệu Năng Cao & Thread-Safe**: Sử dụng `ExecutorService` thread pool (50 workers), `CopyOnWriteArrayList` và `AtomicInteger` giúp chịu tải hàng trăm kết nối đồng thời.
- 🐳 **Dockerized & Unit Tested**: Sẵn sàng đóng gói Docker Compose 1-click và bao phủ kiểm thử tự động với JUnit 4.

---

## 📐 Kiến Trúc Hệ Thống

```
                                  ┌───────────────────────────────────────────────────────────┐
                                  │                SecureChat Server (Multi-Port)             │
                                  │                                                           │
   ┌──────────────────────┐  TCP  │  ┌──────────────────────┐      ┌──────────────────────┐   │
   │  Java Desktop Client │───────┼─>│   TCP Listener :5000 │      │  SQLite DB Manager   │   │
   │  (AES-256 Encrypted) │       │  └──────────┬───────────┘      │  (chat_server.db)    │   │
   └──────────────────────┘       │             │                  └──────────▲───────────┘   │
                                  │             ▼                             │               │
   ┌──────────────────────┐  WS   │  ┌──────────────────────┐                 │               │
   │   Web Browser Client │───────┼─>│  WS Listener   :5001 │─────────────────┤               │
   │   (HTML5/CSS3/JS)    │       │  └──────────┬───────────┘                 │               │
   └──────────────────────┘       │             │                             │               │
                                  │             ▼                             │               │
   ┌──────────────────────┐ HTTP  │  ┌──────────────────────┐                 │               │
   │ Web Static File Host │───────┼─>│  HTTP Server   :5002 │                 │               │
   │ (http://localhost)   │       │  └──────────────────────┘                 │               │
   └──────────────────────┘       │                                           │               │
                                  │  ┌────────────────────────────────────────┴────────────┐  │
                                  │  │  ExecutorService Thread Pool (50 Max Workers)       │  │
                                  │  │  CopyOnWriteArrayList<ClientHandler> State Sync     │  │
                                  │  └─────────────────────────────────────────────────────┘  │
                                  └───────────────────────────────────────────────────────────┘
```

---

## 🛠 Cài Đặt & Hướng Dẫn Vận Hành

### 📋 Yêu Cầu Tiền Đề
* **Java JDK 11** trở lên (Khuyên dùng JDK 17 hoặc 21).
* **Docker & Docker Compose** *(Tùy chọn nếu muốn chạy Container)*.

---

### 🚀 Cách 1: Chạy Trực Tiếp Bằng Java CLI (Referred)

#### Bước 1: Biên dịch tất cả các file Java
```powershell
javac -cp ".;sqlite-jdbc.jar;slf4j-api.jar;slf4j-nop.jar;junit-4.13.2.jar" *.java
```

#### Bước 2: Khởi chạy Server
```powershell
java -cp ".;sqlite-jdbc.jar;slf4j-api.jar;slf4j-nop.jar" Server
```
*Server sẽ tự động lắng nghe trên 3 cổng: **5000** (TCP), **5001** (WebSocket), **5002** (HTTP Web).*

#### Bước 3: Kết nối Client

* **Cách A: Dùng Web UI trên Trình duyệt (Đơn giản nhất)**:
  * Mở trình duyệt web và truy cập địa chỉ: **`http://localhost:5002`**
  * Đăng ký tài khoản mới hoặc đăng nhập để bắt đầu nhắn tin.

* **Cách B: Dùng Java Desktop Client (Mã hóa AES-256)**:
  * Mở một cửa sổ Terminal mới và chạy:
    ```powershell
    java -cp ".;sqlite-jdbc.jar;slf4j-api.jar;slf4j-nop.jar" Client
    ```

---

### 🐳 Cách 2: Chạy Bằng Docker Compose (1-Click Deployment)

Chạy duy nhất một câu lệnh để khởi chạy toàn bộ Server trong môi trường Docker hóa:

```bash
docker-compose up --build -d
```
> Sau khi container khởi chạy thành công, truy cập **`http://localhost:5002`** trên trình duyệt để trải nghiệm!

---

## 🧪 Chạy Kiểm Thử Tự Động (Unit Tests)

Dự án tích hợp sẵn bộ kiểm thử JUnit 4 để xác minh tính đúng đắn của các thuật toán mã hóa và băm mật khẩu:

```powershell
# Run CryptoUtilsTest (Mã hóa AES-256-GCM)
java -cp ".;sqlite-jdbc.jar;slf4j-api.jar;slf4j-nop.jar;junit-4.13.2.jar;hamcrest-core-1.3.jar" org.junit.runner.JUnitCore CryptoUtilsTest

# Run PasswordUtilsTest (Băm mật khẩu PBKDF2)
java -cp ".;sqlite-jdbc.jar;slf4j-api.jar;slf4j-nop.jar;junit-4.13.2.jar;hamcrest-core-1.3.jar" org.junit.runner.JUnitCore PasswordUtilsTest
```

---

## 💬 Danh Sách Lệnh Trong Hệ Thống Chat

| Lệnh Chat | Mô Tả | Ví Dụ |
|---|---|---|
| `/msg <user> <message>` | Gửi tin nhắn riêng riêng tư (Private Message) | `/msg alice Chào bạn!` |
| `/list` | Xem danh sách người dùng đang online | `/list` |
| `/stats` | Xem thống kê Server (Uptime, tổng tin nhắn, online) | `/stats` |
| `/history` | Tải lại 50 tin nhắn lịch sử gần nhất | `/history` |
| `/help` | Hướng dẫn các lệnh hỗ trợ | `/help` |
| `exit` | Đăng xuất và ngắt kết nối an toàn | `exit` |

---

## 🔐 Bảo Mật & Mã Hóa

1. **AES-256-GCM**:
   * Mỗi phiên chạy Server sinh ngẫu nhiên một khoá AES 256-bit trong bộ nhớ.
   * Gói tin mã hóa bao gồm `[12-byte IV] + [Ciphertext] + [16-byte Auth Tag]`.
   * Chống sửa đổi gói tin trên đường truyền (Integrity Verification).
2. **PBKDF2 Password Hashing**:
   * Mật khẩu không bao giờ lưu dưới dạng Plaintext.
   * Cấu hình băm `salt:hash` với 10,000 vòng băm SHA-256 ngăn ngừa tấn công từ điển & Rainbow Table.
3. **WebSocket RFC 6455 RFC Handshake**:
   * Thực hiện bắt tay nghiêm ngặt theo chuẩn IETF mã hóa SHA-1 Base64 cho `Sec-WebSocket-Accept`.

---

## 📁 Cấu Trúc Thư Mục

```
Socket-Project/
├── 📄 Server.java               # Main entry Server (Quản lý Thread Pool & 3 Port)
├── 📄 ClientHandler.java        # Xử lý kết nối TCP Client từng luồng
├── 📄 WebSocketHandler.java     # Xử lý giao thức WebSocket RFC 6455 & JSON Frame
├── 📄 Client.java               # Java Desktop CLI Client (AES-256-GCM)
├── 📄 CryptoUtils.java          # Thư viện mã hóa/giải mã AES-256-GCM
├── 📄 PasswordUtils.java        # Thư viện băm & xác thực mật khẩu PBKDF2
├── 📄 DatabaseManager.java      # Singleton DAO kết nối SQLite (chat_server.db)
├── 📁 web/                      # Giao diện Web Client Modern App
│   ├── 📄 index.html            # Cấu trúc Web Glassmorphic App
│   ├── 📄 style.css             # Design System, Responsive & Animations
│   └── 📄 app.js                # WebSocket Client Controller & Event Handling
├── 📄 CryptoUtilsTest.java      # JUnit 4 Test cho AES-256-GCM
├── 📄 PasswordUtilsTest.java    # JUnit 4 Test cho PBKDF2
├── 📄 Dockerfile                # Multi-stage Docker build recipe
├── 📄 docker-compose.yml        # Docker compose orchestration
├── 📄 CODE_EXPLANATION.md       # Giải thích chi tiết mã nguồn các file
└── 📄 README.md                 # Tài liệu hướng dẫn dự án (File này)
```

---

<div align="center">

Developed with ❤️ by **Minh Quân** | Socket-Project Team 2026

</div>
