import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ClientHandler.java - Per-Client Connection Handler (Server-Side)
 *
 * Each instance runs in a thread from the server's ExecutorService pool.
 * Responsibilities:
 *   - Read the client's nickname on connect
 *   - Route incoming messages (chat, commands, private messages)
 *   - Track connection metadata (IP, join time, message count)
 *   - Handle graceful disconnection and cleanup
 *
 * Supported client commands:
 *   /msg <user> <text>  - Send a private message
 *   /list               - Show online users
 *   /help               - Show available commands
 *   /stats              - Show server statistics
 *   exit                - Disconnect from server
 *
 * @author Socket-Project Team
 */
public class ClientHandler implements Runnable {

    // ──────────────────────────────────────────────────────────────────
    //  Fields
    // ──────────────────────────────────────────────────────────────────

    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;
    private String nickname;
    private volatile boolean running = true;

    // Connection metadata
    private final String clientIP;
    private final LocalDateTime joinTime;
    private final AtomicInteger messageCount = new AtomicInteger(0);

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    // ──────────────────────────────────────────────────────────────────
    //  Constructor
    // ──────────────────────────────────────────────────────────────────

    /**
     * Creates a handler for the given client socket.
     * Captures the client's IP address immediately (before the socket
     * could potentially close).
     *
     * @param socket The TCP socket connected to the client.
     */
    public ClientHandler(Socket socket) {
        this.socket = socket;
        this.clientIP = socket.getInetAddress().getHostAddress();
        this.joinTime = LocalDateTime.now();
    }

    // ──────────────────────────────────────────────────────────────────
    //  Public API (called from Server)
    // ──────────────────────────────────────────────────────────────────

    /**
     * Sends a message to THIS client. Thread-safe — can be called from
     * any thread (broadcast, unicast, admin commands).
     *
     * @param message The text to send.
     */
    public void sendMessage(String message) {
        if (writer != null && !socket.isClosed()) {
            writer.println(message);
        }
    }

    /** Returns this client's nickname. */
    public String getNickname() {
        return nickname;
    }

    /** Returns this client's IP address. */
    public String getClientIP() {
        return clientIP;
    }

    /** Returns the number of messages this client has sent. */
    public int getMessageCount() {
        return messageCount.get();
    }

    /** Returns a formatted join time string. */
    public String getJoinTime() {
        return joinTime.format(TIME_FMT);
    }

    /**
     * Forces this client to disconnect. Used by admin /kick and server shutdown.
     * Sets the running flag to false and closes the socket, which causes
     * the readLine() in run() to throw IOException, breaking the loop.
     */
    public void disconnect() {
        running = false;
        try {
            if (!socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            Server.log("ERROR", "Error disconnecting " + nickname + ": " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  Main Thread Loop
    // ──────────────────────────────────────────────────────────────────

    @Override
    public void run() {
        try {
            // Initialize I/O streams on the socket.
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);

            // ── Step 1: Read Nickname ──
            // The client sends its nickname as the very first line.
            this.nickname = reader.readLine();

            if (this.nickname == null || this.nickname.trim().isEmpty()) {
                this.nickname = "Anonymous";
            }
            this.nickname = this.nickname.trim();

            // Check for duplicate nicknames
            if (Server.getClientByName(this.nickname) != null
                    && Server.getClientByName(this.nickname) != this) {
                sendMessage("[SERVER] Nickname '" + this.nickname + "' is already taken. "
                        + "You have been assigned: " + this.nickname + "_" + clientIP.hashCode());
                this.nickname = this.nickname + "_" + Math.abs(clientIP.hashCode() % 1000);
            }

            Server.log("JOIN", nickname + " joined from " + clientIP);

            // ── Step 2: Announce Join ──
            Server.broadcast("[SERVER] " + nickname + " has joined the chat! "
                    + "(Online: " + Server.getOnlineCount() + ")", this);

            // Send a personal welcome with command help.
            sendMessage("[SERVER] Welcome, " + nickname + "! Online: " + Server.getOnlineCount());
            sendMessage("[SERVER] Commands: /msg <user> <text> | /list | /help | /stats | exit");

            // ── Step 3: Message Loop ──
            String message;
            while (running && (message = reader.readLine()) != null) {
                message = message.trim();

                if (message.isEmpty()) continue;

                // Route the message based on its type (command or chat).
                if (message.equalsIgnoreCase("exit")) {
                    sendMessage("[SERVER] Goodbye, " + nickname + "!");
                    break;
                } else if (message.startsWith("/")) {
                    handleCommand(message);
                } else {
                    // Regular chat message — broadcast to all others.
                    messageCount.incrementAndGet();
                    Server.recordMessage();
                    Server.log("CHAT", nickname + ": " + message);
                    Server.broadcast("[" + nickname + "]: " + message, this);
                }
            }

        } catch (IOException e) {
            // Client disconnected unexpectedly (closed terminal, network loss).
            if (running) {
                Server.log("DISC", (nickname != null ? nickname : "Unknown")
                        + " disconnected unexpectedly.");
            }
        } finally {
            // ── Step 4: Cleanup ──
            // Always runs: announce departure, remove from list, close socket.
            if (nickname != null) {
                Server.broadcast("[SERVER] " + nickname + " has left the chat. "
                        + "(Online: " + (Server.getOnlineCount() - 1) + ")", this);
                Server.log("LEFT", nickname + " left. Messages sent: " + messageCount.get());
            }

            Server.removeClient(this);

            try {
                if (!socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                Server.log("ERROR", "Error closing socket for " + nickname + ": " + e.getMessage());
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  Command Router
    // ──────────────────────────────────────────────────────────────────

    /**
     * Handles slash commands from the client.
     *
     * @param input The raw command string starting with '/'.
     */
    private void handleCommand(String input) {
        String lower = input.toLowerCase();

        if (lower.startsWith("/msg ")) {
            handlePrivateMessage(input);
        } else if (lower.equals("/list")) {
            handleListUsers();
        } else if (lower.equals("/help")) {
            handleHelp();
        } else if (lower.equals("/stats")) {
            sendMessage(Server.getStats());
        } else {
            sendMessage("[SERVER] Unknown command: " + input.split(" ")[0]
                    + ". Type /help for available commands.");
        }
    }

    /**
     * Handles /msg <target> <message> — sends a private message.
     * Format: /msg Alice Hello, how are you?
     */
    private void handlePrivateMessage(String input) {
        // Remove the "/msg " prefix and split into target + message.
        String rest = input.substring(5).trim();
        int spaceIndex = rest.indexOf(' ');

        if (spaceIndex == -1) {
            sendMessage("[SERVER] Usage: /msg <username> <message>");
            return;
        }

        String targetName = rest.substring(0, spaceIndex);
        String privateMsg = rest.substring(spaceIndex + 1).trim();

        if (privateMsg.isEmpty()) {
            sendMessage("[SERVER] Usage: /msg <username> <message>");
            return;
        }

        if (targetName.equalsIgnoreCase(nickname)) {
            sendMessage("[SERVER] You can't send a private message to yourself.");
            return;
        }

        // Format and send the private message.
        String formattedMsg = "[PM from " + nickname + "]: " + privateMsg;
        boolean delivered = Server.unicast(targetName, formattedMsg, this);

        if (delivered) {
            sendMessage("[PM to " + targetName + "]: " + privateMsg);
            Server.log("PM", nickname + " → " + targetName + ": " + privateMsg);
        } else {
            sendMessage("[SERVER] User '" + targetName + "' is not online. "
                    + "Type /list to see online users.");
        }
    }

    /** Handles /list — shows all online users to this client. */
    private void handleListUsers() {
        List<String> users = Server.getOnlineUsers();
        StringBuilder sb = new StringBuilder();
        sb.append("[SERVER] Online users (").append(users.size()).append("):");
        for (int i = 0; i < users.size(); i++) {
            String user = users.get(i);
            String marker = user.equalsIgnoreCase(nickname) ? " (you)" : "";
            sb.append("\n  ").append(i + 1).append(". ").append(user).append(marker);
        }
        sendMessage(sb.toString());
    }

    /** Handles /help — shows available commands to this client. */
    private void handleHelp() {
        sendMessage("╔══════════════════════════════════════════╗");
        sendMessage("║         Available Commands               ║");
        sendMessage("╠══════════════════════════════════════════╣");
        sendMessage("║  /msg <user> <text> - Private message    ║");
        sendMessage("║  /list              - Online users        ║");
        sendMessage("║  /stats             - Server statistics   ║");
        sendMessage("║  /help              - Show this help      ║");
        sendMessage("║  exit               - Leave the chat      ║");
        sendMessage("╚══════════════════════════════════════════╝");
    }
}
