# TCP Chat Server — Production-Grade Socket Application

A real-time, multi-client TCP chat server and client built in Java, demonstrating production-grade networking, concurrency, and systems design.

> Built to showcase **Cloud Engineering** competencies: networked services, thread management, protocol design, graceful lifecycle management, and operational observability.

---

## Architecture

```
                    ┌─────────────────────────────────────────────┐
                    │              SERVER (Port 5000)              │
                    │                                             │
                    │  ┌───────────────────────────────────────┐  │
                    │  │     ExecutorService Thread Pool        │  │
                    │  │           (50 threads max)             │  │
                    │  │                                       │  │
                    │  │  ┌─────────┐ ┌─────────┐ ┌─────────┐│  │
                    │  │  │Handler 1│ │Handler 2│ │Handler N││  │
                    │  │  └────┬────┘ └────┬────┘ └────┬────┘│  │
                    │  └───────┼───────────┼───────────┼─────┘  │
                    │          │           │           │         │
                    │  CopyOnWriteArrayList<ClientHandler>      │
                    │          │           │           │         │
                    │  ┌───────┴───────────┴───────────┴─────┐  │
                    │  │   broadcast() / unicast() / routing  │  │
                    │  └─────────────────────────────────────┘  │
                    │                                             │
                    │  ┌──────────────────────────────────────┐  │
                    │  │  Admin Console (daemon thread)        │  │
                    │  │  /list /kick /stats /say /shutdown    │  │
                    │  └──────────────────────────────────────┘  │
                    └──────────┬──────────┬──────────┬───────────┘
                               │          │          │
                          TCP  │     TCP  │     TCP  │
                               │          │          │
                    ┌──────────┴┐ ┌───────┴───┐ ┌───┴──────────┐
                    │  Client 1  │ │  Client 2  │ │  Client N    │
                    │ ┌────────┐ │ │ ┌────────┐ │ │ ┌────────┐  │
                    │ │ Sender │ │ │ │ Sender │ │ │ │ Sender │  │
                    │ │ Thread │ │ │ │ Thread │ │ │ │ Thread │  │
                    │ ├────────┤ │ │ ├────────┤ │ │ ├────────┤  │
                    │ │Listener│ │ │ │Listener│ │ │ │Listener│  │
                    │ │ Thread │ │ │ │ Thread │ │ │ │ Thread │  │
                    │ └────────┘ │ │ └────────┘ │ │ └────────┘  │
                    └────────────┘ └────────────┘ └─────────────┘
```

---

## Features

| Feature | Description | Cloud Relevance |
|---|---|---|
| **Thread Pool** | `ExecutorService` with 50-thread limit | Resource management, capacity planning |
| **Graceful Shutdown** | `ShutdownHook` + `/shutdown` command | Zero-downtime deployments, signal handling |
| **Private Messaging** | `/msg <user> <text>` unicast routing | Message routing, pub/sub concepts |
| **Online User Listing** | `/list` shows connected clients | Service discovery, health checks |
| **Admin Console** | `/kick`, `/stats`, `/say`, `/shutdown` | Operational tooling, admin interfaces |
| **Server Statistics** | Uptime, connections, messages counters | Observability, monitoring metrics |
| **Connection Metadata** | IP, join time, message count per user | Audit logging, connection tracking |
| **Configurable Ports** | CLI args: `--port 8080` | Infrastructure configuration |
| **Duplicate Detection** | Prevents nickname collisions | Identity management |
| **Structured Logging** | `[timestamp] [CATEGORY] message` | Log aggregation (ELK, CloudWatch) |
| **Thread-Safe State** | `CopyOnWriteArrayList`, `AtomicInteger` | Concurrent systems design |

---

## Quick Start

### Prerequisites
- Java JDK 8+

### 1. Compile
```bash
javac Server.java ClientHandler.java Client.java
```

### 2. Start the Server
```bash
# Default port (5000)
java Server

# Custom port
java Server 8080
java Server --port 8080
```

### 3. Connect Clients (open separate terminals)
```bash
# Default (localhost:5000)
java Client

# Custom host/port
java Client 192.168.1.10 8080
java Client --host 10.0.0.1 --port 8080
```

---

## Commands

### Client Commands
| Command | Description |
|---|---|
| `/msg <user> <text>` | Send a private message |
| `/list` | Show online users |
| `/stats` | Show server statistics |
| `/help` | Show available commands |
| `exit` | Disconnect from server |

### Server Admin Commands
| Command | Description |
|---|---|
| `/list` | Show online users with metadata |
| `/kick <user>` | Disconnect a user |
| `/stats` | Show server statistics |
| `/say <message>` | Broadcast a server announcement |
| `/shutdown` | Gracefully shut down the server |
| `/help` | Show admin commands |

---

## Cloud Engineering Concepts Demonstrated

| Concept | Implementation |
|---|---|
| **TCP/IP Networking** | Raw socket programming, client-server model |
| **Concurrency** | Thread pool, thread-safe collections, atomic operations |
| **Protocol Design** | Custom text-based protocol with command routing |
| **Graceful Lifecycle** | Shutdown hooks, clean resource cleanup |
| **Observability** | Structured logging, server statistics, connection metadata |
| **Configuration** | CLI argument parsing for host/port |
| **Resource Management** | `ExecutorService`, try-with-resources, socket cleanup |
| **Scalability** | Thread pool limits, non-blocking broadcast |

---

## Project Structure

```
Socket-Project/
├── Server.java          # TCP server with thread pool & admin console
├── ClientHandler.java   # Per-client handler with command routing
├── Client.java          # Chat client with listener thread
├── code_explanation.txt  # Detailed code walkthrough
├── README.md            # This file
├── .gitignore
└── LICENSE
```

---

## License

See [LICENSE](LICENSE) for details.
