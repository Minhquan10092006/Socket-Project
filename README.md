<div align="center">

# 🔒 SecureChat Multi-Protocol v3.0

### Production-Grade Encrypted Real-Time Chat System

[![Java](https://img.shields.io/badge/Java-11%2B-orange.svg?style=for-the-badge&logo=openjdk)](https://www.oracle.com/java/)
[![Security](https://img.shields.io/badge/Encryption-AES--256--GCM-blue.svg?style=for-the-badge)](https://en.wikipedia.org/wiki/Galois/Counter_Mode)
[![Database](https://img.shields.io/badge/Database-SQLite-003B57.svg?style=for-the-badge&logo=sqlite)](https://www.sqlite.org/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED.svg?style=for-the-badge&logo=docker)](https://www.docker.com/)

A multi-protocol chat system supporting TCP, WebSocket, and HTTP communication.

It includes AES-256-GCM encryption, PBKDF2 password hashing, SQLite persistence,
a Java command-line client, and a modern web interface.

[Features](#-key-features) •
[Architecture](#-system-architecture) •
[Installation](#-installation-and-usage) •
[Commands](#-chat-commands) •
[Security](#-security-and-encryption)

</div>

---

## 🌟 Key Features

- **AES-256-GCM encryption** for TCP client-server communication.
- **PBKDF2 password hashing** with a random salt.
- **SQLite database storage** for users and chat history.
- **Multi-protocol communication**:
  - TCP server on port `5000`.
  - WebSocket server on port `5001`.
  - HTTP server on port `5002`.
- **Java command-line client** with real-time messaging.
- **Web chat interface** with responsive dark-mode design.
- **Private messaging** between connected users.
- **Online-user listing** and server statistics.
- **Chat history support** for recent messages.
- **Thread pool support** for handling multiple clients.
- **Docker Compose support**.
- **JUnit tests** for encryption and password utilities.

---

## 📐 System Architecture

```text
Java Client ───── TCP :5000 ─────┐
                                 │
Web Browser ─── WebSocket :5001 ─┼── SecureChat Server
                                 │
Web Browser ───── HTTP :5002 ────┘
                                      │
                                      ▼
                              SQLite Database
                              chat_server.db
```

The Java client communicates with the server through TCP.
The browser client uses WebSocket for real-time communication and HTTP to load
the web interface.

---

## 🛠 Installation and Usage

### Prerequisites

- Java JDK 11 or later.
- JDK 17 or 21 is recommended.
- SQLite JDBC driver.
- Docker and Docker Compose are optional.

### Required Libraries

Place the following `.jar` files in the project directory if they are required
by the source code:

- `sqlite-jdbc.jar`
- `slf4j-api.jar`
- `slf4j-nop.jar`
- `junit-4.13.2.jar`
- `hamcrest-core-1.3.jar`

### Compile the Project

Open PowerShell in the project directory:

```powershell
javac -cp ".;sqlite-jdbc.jar;slf4j-api.jar;slf4j-nop.jar" *.java
```

### Start the Server

```powershell
java -cp ".;sqlite-jdbc.jar;slf4j-api.jar;slf4j-nop.jar" Server
```

The server uses these ports:

| Port | Protocol | Purpose |
|---:|---|---|
| `5000` | TCP | Java client connections |
| `5001` | WebSocket | Browser real-time communication |
| `5002` | HTTP | Web interface |

### Run the Java Client

Open another PowerShell window:

```powershell
java -cp ".;sqlite-jdbc.jar;slf4j-api.jar;slf4j-nop.jar" Client
```

The client uses `localhost:5000` by default.

You can also specify a custom host and port:

```powershell
java Client 192.168.1.10
java Client 192.168.1.10 8080
java Client --host 192.168.1.10 --port 8080
```

### Open the Web Interface

Open a browser and visit:

```text
http://localhost:5002
```

Register an account or log in to start chatting.

---

## 🐳 Run with Docker Compose

Build and start the application:

```powershell
docker compose up --build -d
```

View container logs:

```powershell
docker compose logs -f
```

Stop the application:

```powershell
docker compose down
```

Then open:

```text
http://localhost:5002
```

---

## 💬 Chat Commands

| Command | Description | Example |
|---|---|---|
| `/msg <user> <message>` | Send a private message | `/msg alice Hello!` |
| `/list` | Display online users | `/list` |
| `/stats` | Display server statistics | `/stats` |
| `/history` | Display recent chat history | `/history` |
| `/help` | Display available commands | `/help` |
| `exit` | Disconnect from the server | `exit` |

---

## 🔐 Security and Encryption

### AES-256-GCM

The TCP client uses AES-256-GCM to encrypt messages.

Encrypted data contains:

```text
[12-byte IV] + [Ciphertext] + [16-byte Authentication Tag]
```

The authentication tag helps detect modified or corrupted messages.

### PBKDF2 Password Hashing

- Passwords are not stored as plaintext.
- Each password uses a random salt.
- Passwords are processed using PBKDF2 with HMAC-SHA256.
- Password data is stored in a `salt:hash` format.

### Important Security Note

The encryption key is received from the server during the connection handshake.
For production deployment, use TLS or another authenticated key-exchange method
to protect the key and user credentials.

---

## 🧪 Running Unit Tests

Run the encryption tests:

```powershell
java -cp ".;sqlite-jdbc.jar;slf4j-api.jar;slf4j-nop.jar;junit-4.13.2.jar;hamcrest-core-1.3.jar" org.junit.runner.JUnitCore CryptoUtilsTest
```

Run the password hashing tests:

```powershell
java -cp ".;sqlite-jdbc.jar;slf4j-api.jar;slf4j-nop.jar;junit-4.13.2.jar;hamcrest-core-1.3.jar" org.junit.runner.JUnitCore PasswordUtilsTest
```

---

## 📁 Project Structure

```text
Socket-Project/
├── Server.java               # Main server entry point
├── Client.java               # Java command-line client
├── ClientHandler.java        # Handles TCP client connections
├── WebSocketHandler.java     # Handles WebSocket connections
├── CryptoUtils.java          # AES-256-GCM encryption utilities
├── PasswordUtils.java        # PBKDF2 password utilities
├── DatabaseManager.java      # SQLite database access
├── chat_server.db            # SQLite database created at runtime
├── web/
│   ├── index.html            # Web application layout
│   ├── style.css             # Web application styles
│   └── app.js                # WebSocket client logic
├── CryptoUtilsTest.java      # Encryption tests
├── PasswordUtilsTest.java    # Password hashing tests
├── Dockerfile                # Docker image configuration
├── docker-compose.yml        # Docker Compose configuration
├── CODE_EXPLANATION.md       # Source-code documentation
└── README.md                 # Project documentation
```

---

## ⚠️ Troubleshooting

### The client cannot connect

Make sure the server is running and that the client uses the correct host and
port.

```powershell
java Server
java Client localhost 5000
```

### A port is already in use

Start the server on an available port and connect the client to that port.

### The web interface does not load

Verify that:

1. The server is running.
2. Port `5002` is available.
3. The `web` directory is present.
4. Windows Firewall allows Java network access.

---

## 📌 Limitations

- This is a console and browser-based application.
- The server must be running before clients connect.
- The current key-exchange process is intended for educational use.
- TLS should be added before using the system in production.
- Database credentials and configuration should be secured in production.

---

## 📄 License

This project is provided for educational and demonstration purposes.

---

<div align="center">

Developed by **Minh Quân** | Socket-Project Team 2026
