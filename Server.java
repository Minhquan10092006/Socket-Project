import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.SecretKey;

/**
 * Server.java - Production-Grade Multi-Client TCP Chat Server
 *
 * Features:
 * - Thread pool (ExecutorService) for managed concurrency
 * - Server admin console (/kick, /list, /shutdown, /stats)
 * - Graceful shutdown hook for clean resource cleanup
 * - Private messaging support (unicast)
 * - Online user listing
 * - Configurable port via command-line arguments
 * - Server-side statistics (connections, messages, uptime)
 * - Timestamped structured logging
 * - AES-256-GCM encryption for all messages (end-to-end security)
 *
 * Usage:
 * java Server → starts on default port 5000
 * java Server 8080 → starts on port 8080
 * java Server --port 8080 → starts on port 8080
 *
 * @author Socket-Project Team
 */
public class Server {

    // ──────────────────────────────────────────────────────────────────
    // Constants
    // ──────────────────────────────────────────────────────────────────
    private static final int DEFAULT_PORT = 5000;
    private static final int WS_PORT_OFFSET = 1;  // WebSocket port = TCP port + 1
    private static final int HTTP_PORT_OFFSET = 2; // HTTP file server port = TCP port + 2
    private static final int THREAD_POOL_SIZE = 50;
    private static final DateTimeFormatter LOG_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ──────────────────────────────────────────────────────────────────
    // Shared State (thread-safe)
    // ──────────────────────────────────────────────────────────────────

    /** Thread-safe list of all active client handlers. */
    public static final CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<>();

    /** Thread pool that manages client handler threads. */
    private static ExecutorService threadPool;

    /** Server socket reference (used by shutdown hook). */
    private static ServerSocket serverSocket;

    // ──────────────────────────────────────────────────────────────────
    // Encryption
    // ──────────────────────────────────────────────────────────────────

    /** Shared AES-256 encryption key (distributed to all clients on connect). */
    private static SecretKey encryptionKey;

    /** Base64-encoded encryption key string (sent to clients). */
    private static String encryptionKeyString;

    // ──────────────────────────────────────────────────────────────────
    // Statistics (atomic for thread safety)
    // ──────────────────────────────────────────────────────────────────
    private static final AtomicInteger totalConnections = new AtomicInteger(0);
    private static final AtomicLong totalMessages = new AtomicLong(0);
    private static LocalDateTime startTime;

    // ──────────────────────────────────────────────────────────────────
    // Entry Point
    // ──────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        int port = parsePort(args);
        startTime = LocalDateTime.now();

        // Generate AES-256 encryption key for this server session.
        try {
            encryptionKey = CryptoUtils.generateKey();
            encryptionKeyString = CryptoUtils.keyToString(encryptionKey);
            log("CRYPTO", "AES-256-GCM encryption key generated successfully.");
        } catch (Exception e) {
            log("ERROR", "Failed to generate encryption key: " + e.getMessage());
            System.exit(1);
        }

        // Initialize SQLite database for user accounts and message history.
        DatabaseManager.getInstance();

        // Create a fixed thread pool. This limits the max number of
        // concurrent clients and reuses threads, which is much more
        // efficient than creating a new Thread for each connection.
        threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        // Register a shutdown hook so the server cleans up gracefully
        // when killed (Ctrl+C, SIGTERM, etc.)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log("INFO", "Shutdown hook triggered. Cleaning up...");
            shutdownServer();
        }));

        try {
            serverSocket = new ServerSocket(port);
            printBanner(port);

            // Start the admin console in a separate daemon thread.
            Thread adminThread = new Thread(Server::adminConsole);
            adminThread.setDaemon(true);
            adminThread.start();

            // Start WebSocket listener in a separate daemon thread.
            int wsPort = port + WS_PORT_OFFSET;
            Thread wsThread = new Thread(() -> webSocketAcceptLoop(wsPort));
            wsThread.setDaemon(true);
            wsThread.start();

            // Start HTTP file server for web UI in a separate daemon thread.
            int httpPort = port + HTTP_PORT_OFFSET;
            Thread httpThread = new Thread(() -> httpFileServer(httpPort));
            httpThread.setDaemon(true);
            httpThread.start();

            // Main accept loop: continuously accept new client connections.
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    totalConnections.incrementAndGet();

                    String clientIP = socket.getInetAddress().getHostAddress();
                    log("CONN", "New connection from " + clientIP
                            + " (total: " + totalConnections.get() + ")");

                    ClientHandler handler = new ClientHandler(socket, encryptionKey);
                    clients.add(handler);

                    // Submit to the thread pool instead of creating a raw thread.
                    threadPool.submit(handler);

                } catch (SocketException e) {
                    // ServerSocket was closed (e.g., by /shutdown command)
                    if (serverSocket.isClosed()) {
                        log("INFO", "Server socket closed. Exiting accept loop.");
                        break;
                    }
                }
            }

        } catch (IOException e) {
            log("ERROR", "Failed to start server: " + e.getMessage());
            e.printStackTrace();
        } finally {
            shutdownServer();
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Messaging Methods
    // ──────────────────────────────────────────────────────────────────

    /**
     * Broadcasts a message to all connected clients except the sender.
     *
     * @param message The message to broadcast.
     * @param sender  The sender (excluded from broadcast). Can be null for server
     *                messages.
     */
    public static void broadcast(String message, ClientHandler sender) {
        for (ClientHandler client : clients) {
            if (client != sender) {
                client.sendMessage(message);
            }
        }
    }

    /**
     * Sends a private message to a specific client by nickname.
     *
     * @param targetName The nickname of the target client.
     * @param message    The private message to deliver.
     * @param sender     The sender's handler (for error feedback).
     * @return true if the message was delivered, false if the user was not found.
     */
    public static boolean unicast(String targetName, String message, ClientHandler sender) {
        ClientHandler target = getClientByName(targetName);
        if (target != null) {
            target.sendMessage(message);
            return true;
        }
        return false;
    }

    // ──────────────────────────────────────────────────────────────────
    // Client Management
    // ──────────────────────────────────────────────────────────────────

    /**
     * Removes a client handler from the active list.
     *
     * @param handler The handler to remove.
     */
    public static void removeClient(ClientHandler handler) {
        clients.remove(handler);
    }

    /**
     * Finds a client handler by the client's nickname (case-insensitive).
     *
     * @param name The nickname to search for.
     * @return The matching ClientHandler, or null if not found.
     */
    public static ClientHandler getClientByName(String name) {
        for (ClientHandler client : clients) {
            if (client.getNickname() != null
                    && client.getNickname().equalsIgnoreCase(name)) {
                return client;
            }
        }
        return null;
    }

    /**
     * Returns a list of all currently connected nicknames.
     *
     * @return List of online user nicknames.
     */
    public static List<String> getOnlineUsers() {
        List<String> users = new ArrayList<>();
        for (ClientHandler client : clients) {
            String name = client.getNickname();
            if (name != null) {
                users.add(name);
            }
        }
        return users;
    }

    /**
     * Returns the current number of connected clients.
     */
    public static int getOnlineCount() {
        return clients.size();
    }

    // ──────────────────────────────────────────────────────────────────
    // Statistics
    // ──────────────────────────────────────────────────────────────────

    /** Increments the global message counter. Called by ClientHandler. */
    public static void recordMessage() {
        totalMessages.incrementAndGet();
    }

    /** Returns formatted server statistics. */
    public static String getStats() {
        Duration uptime = Duration.between(startTime, LocalDateTime.now());
        long hours = uptime.toHours();
        long minutes = uptime.toMinutes() % 60;
        long seconds = uptime.getSeconds() % 60;

        DatabaseManager db = DatabaseManager.getInstance();

        return String.format(
                "Server Statistics:\n"
                        + "  Uptime:             %02d:%02d:%02d\n"
                        + "  Online clients:     %d\n"
                        + "  Total connections:  %d\n"
                        + "  Messages relayed:   %d\n"
                        + "  Thread pool size:   %d\n"
                        + "  Encryption:         AES-256-GCM\n"
                        + "  Registered users:   %d\n"
                        + "  Stored messages:    %d",
                hours, minutes, seconds,
                getOnlineCount(),
                totalConnections.get(),
                totalMessages.get(),
                THREAD_POOL_SIZE,
                db.getTotalUserCount(),
                db.getTotalMessageCount());
    }

    // ──────────────────────────────────────────────────────────────────
    // Admin Console (runs in a daemon thread)
    // ──────────────────────────────────────────────────────────────────

    /**
     * Server-side admin console. Reads commands from System.in.
     * Available commands:
     * /list - show all connected users
     * /kick <n> - disconnect a user by nickname
     * /stats - show server statistics
     * /say <m> - broadcast a server announcement
     * /shutdown - gracefully shut down the server
     * /help - show available commands
     */
    private static void adminConsole() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            try {
                if (!scanner.hasNextLine())
                    break;
                String input = scanner.nextLine().trim();
                if (input.isEmpty())
                    continue;

                if (input.equalsIgnoreCase("/list")) {
                    adminListUsers();
                } else if (input.toLowerCase().startsWith("/kick ")) {
                    String target = input.substring(6).trim();
                    adminKickUser(target);
                } else if (input.equalsIgnoreCase("/stats")) {
                    System.out.println(getStats());
                } else if (input.toLowerCase().startsWith("/say ")) {
                    String msg = input.substring(5).trim();
                    if (!msg.isEmpty()) {
                        broadcast("[SERVER ANNOUNCEMENT] " + msg, null);
                        log("ADMIN", "Broadcast: " + msg);
                    }
                } else if (input.equalsIgnoreCase("/shutdown")) {
                    log("ADMIN", "Shutdown requested by admin.");
                    broadcast("[SERVER] Server is shutting down. Goodbye!", null);
                    shutdownServer();
                    System.exit(0);
                } else if (input.equalsIgnoreCase("/help")) {
                    adminHelp();
                } else {
                    System.out.println("Unknown command. Type /help for available commands.");
                }
            } catch (Exception e) {
                // Ignore scanner exceptions during shutdown
                break;
            }
        }
        scanner.close();
    }

    private static void adminListUsers() {
        List<String> users = getOnlineUsers();
        if (users.isEmpty()) {
            System.out.println("No users currently online.");
        } else {
            System.out.println("Online users (" + users.size() + "):");
            for (int i = 0; i < users.size(); i++) {
                ClientHandler handler = clients.get(i);
                System.out.printf("  %d. %-15s [IP: %s, Messages: %d, Joined: %s]%n",
                        i + 1,
                        handler.getNickname(),
                        handler.getClientIP(),
                        handler.getMessageCount(),
                        handler.getJoinTime());
            }
        }
    }

    private static void adminKickUser(String targetName) {
        ClientHandler target = getClientByName(targetName);
        if (target == null) {
            System.out.println("User '" + targetName + "' not found.");
        } else {
            target.sendMessage("[SERVER] You have been kicked by an admin.");
            target.disconnect();
            log("ADMIN", "Kicked user: " + targetName);
        }
    }

    private static void adminHelp() {
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║       Server Admin Commands            ║");
        System.out.println("╠════════════════════════════════════════╣");
        System.out.println("║  /list      - Show online users        ║");
        System.out.println("║  /kick <n>  - Kick a user by name      ║");
        System.out.println("║  /stats     - Show server statistics    ║");
        System.out.println("║  /say <msg> - Broadcast announcement   ║");
        System.out.println("║  /shutdown  - Shut down the server      ║");
        System.out.println("║  /help      - Show this help            ║");
        System.out.println("╚════════════════════════════════════════╝");
    }

    // ──────────────────────────────────────────────────────────────────
    // Shutdown
    // ──────────────────────────────────────────────────────────────────

    /** Gracefully shuts down the server: close all clients, pool, database, and socket. */
    private static void shutdownServer() {
        // Notify all clients
        for (ClientHandler client : clients) {
            client.sendMessage("[SERVER] Server is shutting down.");
            client.disconnect();
        }
        clients.clear();

        // Shut down the thread pool
        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdownNow();
            log("INFO", "Thread pool shut down.");
        }

        // Close the database connection
        DatabaseManager.getInstance().close();

        // Close the server socket
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                log("INFO", "Server socket closed.");
            } catch (IOException e) {
                log("ERROR", "Error closing server socket: " + e.getMessage());
            }
        }

        log("INFO", "Server shut down complete.");
    }

    // ──────────────────────────────────────────────────────────────────
    // Utilities
    // ──────────────────────────────────────────────────────────────────

    /** Parses the port from command-line arguments. */
    private static int parsePort(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--port") && i + 1 < args.length) {
                try {
                    return Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    System.out.println("[WARN] Invalid port: " + args[i + 1]
                            + ". Using default " + DEFAULT_PORT);
                }
            } else if (i == 0 && !args[i].startsWith("-")) {
                try {
                    return Integer.parseInt(args[i]);
                } catch (NumberFormatException e) {
                    // Not a number, ignore
                }
            }
        }
        return DEFAULT_PORT;
    }

    /** Returns the encryption key string to send to clients during handshake. */
    public static String getEncryptionKeyString() {
        return encryptionKeyString;
    }

    /** Returns the encryption key. */
    public static SecretKey getEncryptionKey() {
        return encryptionKey;
    }

    /** Structured logging with timestamp and category. */
    public static void log(String category, String message) {
        String timestamp = LocalDateTime.now().format(LOG_FMT);
        System.out.printf("[%s] [%s] %s%n", timestamp, category, message);
    }

    /** Prints the server startup banner. */
    private static void printBanner(int port) {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║       TCP Chat Server v3.0                   ║");
        System.out.println("║       Encrypted Production Socket Server     ║");
        System.out.println("╠══════════════════════════════════════════════╣");
        System.out.printf("║  TCP Port:    %-30d ║%n", port);
        System.out.printf("║  WS Port:     %-30d ║%n", port + WS_PORT_OFFSET);
        System.out.printf("║  Web UI:      %-30s ║%n", "http://localhost:" + (port + HTTP_PORT_OFFSET));
        System.out.printf("║  Thread Pool: %-30d ║%n", THREAD_POOL_SIZE);
        System.out.printf("║  Encryption:  %-30s ║%n", "AES-256-GCM");
        System.out.printf("║  Started:     %-30s ║%n", startTime.format(LOG_FMT));
        System.out.println("║  Status:      READY (ENCRYPTED)              ║");
        System.out.println("╠══════════════════════════════════════════════╣");
        System.out.println("║  Type /help for admin commands               ║");
        System.out.println("╚══════════════════════════════════════════════╝");
    }

    // ──────────────────────────────────────────────────────────────────
    // WebSocket Accept Loop
    // ──────────────────────────────────────────────────────────────────

    /**
     * Listens for WebSocket connections on a separate port.
     * Each connection is handled by a WebSocketHandler in the thread pool.
     */
    private static void webSocketAcceptLoop(int wsPort) {
        try (ServerSocket wsServerSocket = new ServerSocket(wsPort)) {
            log("WS", "WebSocket server listening on port " + wsPort);

            while (!wsServerSocket.isClosed()) {
                try {
                    Socket wsSocket = wsServerSocket.accept();
                    log("WS", "WebSocket connection from " + wsSocket.getInetAddress().getHostAddress());

                    WebSocketHandler wsHandler = new WebSocketHandler(wsSocket, encryptionKey);
                    threadPool.submit(wsHandler);

                } catch (SocketException e) {
                    break;
                }
            }
        } catch (IOException e) {
            log("ERROR", "WebSocket server error: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // HTTP File Server (serves web UI)
    // ──────────────────────────────────────────────────────────────────

    /**
     * Simple HTTP file server that serves the web chat UI files.
     * Supports index.html, style.css, and app.js from the "web/" directory.
     */
    private static void httpFileServer(int httpPort) {
        try (ServerSocket httpSocket = new ServerSocket(httpPort)) {
            log("HTTP", "Web UI available at http://localhost:" + httpPort);

            while (!httpSocket.isClosed()) {
                try {
                    Socket client = httpSocket.accept();
                    threadPool.submit(() -> handleHttpRequest(client));
                } catch (SocketException e) {
                    break;
                }
            }
        } catch (IOException e) {
            log("ERROR", "HTTP server error: " + e.getMessage());
        }
    }

    /**
     * Handles a single HTTP request. Serves static files from the "web/" directory.
     */
    private static void handleHttpRequest(Socket client) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
             OutputStream out = client.getOutputStream()) {

            // Read the request line
            String requestLine = reader.readLine();
            if (requestLine == null) return;

            // Parse the requested path
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;

            String path = parts[1];
            if (path.equals("/")) path = "/index.html";

            // Determine file path and content type
            String filePath = "web" + path;
            String contentType = getContentType(path);

            File file = new File(filePath);
            if (file.exists() && file.isFile()) {
                byte[] content = readFileBytes(file);
                String response = "HTTP/1.1 200 OK\r\n"
                        + "Content-Type: " + contentType + "\r\n"
                        + "Content-Length: " + content.length + "\r\n"
                        + "Access-Control-Allow-Origin: *\r\n"
                        + "Cache-Control: no-cache\r\n"
                        + "\r\n";
                out.write(response.getBytes());
                out.write(content);
            } else {
                String notFound = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n";
                out.write(notFound.getBytes());
            }

            out.flush();
        } catch (IOException e) {
            // Connection closed
        }
    }

    private static String getContentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=UTF-8";
        if (path.endsWith(".css")) return "text/css; charset=UTF-8";
        if (path.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".ico")) return "image/x-icon";
        if (path.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    private static byte[] readFileBytes(File file) throws IOException {
        byte[] bytes = new byte[(int) file.length()];
        try (FileInputStream fis = new FileInputStream(file)) {
            fis.read(bytes);
        }
        return bytes;
    }
}