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

/**
 * Server.java - Production-Grade Multi-Client TCP Chat Server
 *
 * Features:
 *   - Thread pool (ExecutorService) for managed concurrency
 *   - Server admin console (/kick, /list, /shutdown, /stats)
 *   - Graceful shutdown hook for clean resource cleanup
 *   - Private messaging support (unicast)
 *   - Online user listing
 *   - Configurable port via command-line arguments
 *   - Server-side statistics (connections, messages, uptime)
 *   - Timestamped structured logging
 *
 * Usage:
 *   java Server              → starts on default port 5000
 *   java Server 8080         → starts on port 8080
 *   java Server --port 8080  → starts on port 8080
 *
 * @author Socket-Project Team
 */
public class Server {

    // ──────────────────────────────────────────────────────────────────
    //  Constants
    // ──────────────────────────────────────────────────────────────────
    private static final int DEFAULT_PORT = 5000;
    private static final int THREAD_POOL_SIZE = 50;
    private static final DateTimeFormatter LOG_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ──────────────────────────────────────────────────────────────────
    //  Shared State (thread-safe)
    // ──────────────────────────────────────────────────────────────────

    /** Thread-safe list of all active client handlers. */
    public static final CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<>();

    /** Thread pool that manages client handler threads. */
    private static ExecutorService threadPool;

    /** Server socket reference (used by shutdown hook). */
    private static ServerSocket serverSocket;

    // ──────────────────────────────────────────────────────────────────
    //  Statistics (atomic for thread safety)
    // ──────────────────────────────────────────────────────────────────
    private static final AtomicInteger totalConnections = new AtomicInteger(0);
    private static final AtomicLong totalMessages = new AtomicLong(0);
    private static LocalDateTime startTime;

    // ──────────────────────────────────────────────────────────────────
    //  Entry Point
    // ──────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        int port = parsePort(args);
        startTime = LocalDateTime.now();

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

            // Main accept loop: continuously accept new client connections.
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    totalConnections.incrementAndGet();

                    String clientIP = socket.getInetAddress().getHostAddress();
                    log("CONN", "New connection from " + clientIP
                            + " (total: " + totalConnections.get() + ")");

                    ClientHandler handler = new ClientHandler(socket);
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
    //  Messaging Methods
    // ──────────────────────────────────────────────────────────────────

    /**
     * Broadcasts a message to all connected clients except the sender.
     *
     * @param message The message to broadcast.
     * @param sender  The sender (excluded from broadcast). Can be null for server messages.
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
    //  Client Management
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
    //  Statistics
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

        return String.format(
                "Server Statistics:\n"
              + "  Uptime:             %02d:%02d:%02d\n"
              + "  Online clients:     %d\n"
              + "  Total connections:  %d\n"
              + "  Messages relayed:   %d\n"
              + "  Thread pool size:   %d",
                hours, minutes, seconds,
                getOnlineCount(),
                totalConnections.get(),
                totalMessages.get(),
                THREAD_POOL_SIZE
        );
    }

    // ──────────────────────────────────────────────────────────────────
    //  Admin Console (runs in a daemon thread)
    // ──────────────────────────────────────────────────────────────────

    /**
     * Server-side admin console. Reads commands from System.in.
     * Available commands:
     *   /list     - show all connected users
     *   /kick <n> - disconnect a user by nickname
     *   /stats    - show server statistics
     *   /say <m>  - broadcast a server announcement
     *   /shutdown  - gracefully shut down the server
     *   /help     - show available commands
     */
    private static void adminConsole() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            try {
                if (!scanner.hasNextLine()) break;
                String input = scanner.nextLine().trim();
                if (input.isEmpty()) continue;

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
    //  Shutdown
    // ──────────────────────────────────────────────────────────────────

    /** Gracefully shuts down the server: close all clients, pool, and socket. */
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
    //  Utilities
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

    /** Structured logging with timestamp and category. */
    public static void log(String category, String message) {
        String timestamp = LocalDateTime.now().format(LOG_FMT);
        System.out.printf("[%s] [%s] %s%n", timestamp, category, message);
    }

    /** Prints the server startup banner. */
    private static void printBanner(int port) {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║       TCP Chat Server v2.0                   ║");
        System.out.println("║       Production-Grade Socket Server         ║");
        System.out.println("╠══════════════════════════════════════════════╣");
        System.out.printf( "║  Port:        %-30d ║%n", port);
        System.out.printf( "║  Thread Pool: %-30d ║%n", THREAD_POOL_SIZE);
        System.out.printf( "║  Started:     %-30s ║%n", startTime.format(LOG_FMT));
        System.out.println("║  Status:      READY                          ║");
        System.out.println("╠══════════════════════════════════════════════╣");
        System.out.println("║  Type /help for admin commands               ║");
        System.out.println("╚══════════════════════════════════════════════╝");
    }
}