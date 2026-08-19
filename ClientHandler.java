import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.SecretKey;

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

    // Encryption key shared with the server
    private final SecretKey encryptionKey;

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
     * @param socket        The TCP socket connected to the client.
     * @param encryptionKey The AES-256 encryption key for this session.
     */
    public ClientHandler(Socket socket, SecretKey encryptionKey) {
        this.socket = socket;
        this.clientIP = socket.getInetAddress().getHostAddress();
        this.joinTime = LocalDateTime.now();
        this.encryptionKey = encryptionKey;
    }

    // ──────────────────────────────────────────────────────────────────
    //  Public API (called from Server)
    // ──────────────────────────────────────────────────────────────────

    /**
     * Sends an encrypted message to THIS client. Thread-safe — can be called from
     * any thread (broadcast, unicast, admin commands).
     * The message is encrypted with AES-256-GCM before being sent.
     *
     * @param message The plaintext to encrypt and send.
     */
    public void sendMessage(String message) {
        if (writer != null && !socket.isClosed()) {
            try {
                String encrypted = CryptoUtils.encrypt(message, encryptionKey);
                writer.println(encrypted);
            } catch (Exception e) {
                Server.log("ERROR", "Encryption failed for " + nickname + ": " + e.getMessage());
                // Fallback: send plaintext if encryption fails
                writer.println(message);
            }
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

            // ── Step 1: Send encryption key to client ──
            // The key is sent UNENCRYPTED as the very first message.
            // This is the key exchange handshake.
            writer.println("KEY:" + Server.getEncryptionKeyString());
            Server.log("CRYPTO", "Encryption key sent to " + clientIP);

            // ── Step 2: Authentication ──
            // Client must register or login before chatting.
            if (!handleAuthentication()) {
                return; // Authentication failed — disconnect
            }

            Server.log("JOIN", nickname + " joined from " + clientIP);

            // ── Step 3: Announce Join ──
            Server.broadcast("[SERVER] " + nickname + " has joined the chat! "
                    + "(Online: " + Server.getOnlineCount() + ")", this);

            // Send a personal welcome with command help.
            sendMessage("[SERVER] Welcome back, " + nickname + "! Online: " + Server.getOnlineCount());
            sendMessage("[SERVER] Commands: /msg <user> <text> | /list | /help | /stats | /history | exit");
            sendMessage("[SERVER] \uD83D\uDD12 All messages are encrypted with AES-256-GCM.");

            // ── Step 3.5: Send recent chat history ──
            sendChatHistory();

            // ── Step 4: Message Loop ──
            String encryptedMessage;
            while (running && (encryptedMessage = reader.readLine()) != null) {
                // Decrypt the incoming message
                String message;
                try {
                    message = CryptoUtils.decrypt(encryptedMessage, encryptionKey);
                } catch (Exception e) {
                    // If decryption fails, treat as plaintext
                    message = encryptedMessage;
                }
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

                    // Save to database
                    DatabaseManager.getInstance().saveMessage(nickname, message, "BROADCAST", null);
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
        } else if (lower.equals("/history")) {
            sendChatHistory();
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
            Server.log("PM", nickname + " \u2192 " + targetName + ": " + privateMsg);

            // Save PM to database
            DatabaseManager.getInstance().saveMessage(nickname, privateMsg, "PM", targetName);
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
        sendMessage("║  /history           - Chat history        ║");
        sendMessage("║  /help              - Show this help      ║");
        sendMessage("║  exit               - Leave the chat      ║");
        sendMessage("╚══════════════════════════════════════════╝");
    }

    // ──────────────────────────────────────────────────────────────────
    //  Authentication
    // ──────────────────────────────────────────────────────────────────

    /**
     * Handles the authentication flow: register or login.
     * The client sends "AUTH:REGISTER:username:password" or "AUTH:LOGIN:username:password".
     * Returns true if authentication succeeds, false otherwise.
     */
    private boolean handleAuthentication() throws IOException {
        DatabaseManager db = DatabaseManager.getInstance();
        int maxAttempts = 3;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String rawLine = reader.readLine();
            if (rawLine == null) return false;

            // Decrypt the auth message
            String authLine;
            try {
                authLine = CryptoUtils.decrypt(rawLine, encryptionKey);
            } catch (Exception e) {
                authLine = rawLine;
            }

            if (!authLine.startsWith("AUTH:")) {
                sendMessage("[SERVER] Invalid authentication format. Use AUTH:LOGIN or AUTH:REGISTER.");
                continue;
            }

            String[] parts = authLine.split(":", 4);
            if (parts.length < 4) {
                sendMessage("[SERVER] Invalid auth format. Expected: AUTH:LOGIN:username:password");
                continue;
            }

            String action = parts[1].toUpperCase();
            String username = parts[2].trim();
            String password = parts[3];

            if (username.isEmpty() || password.isEmpty()) {
                sendMessage("[SERVER] Username and password cannot be empty.");
                continue;
            }

            if (username.length() < 3 || username.length() > 20) {
                sendMessage("[SERVER] Username must be 3-20 characters long.");
                continue;
            }

            if (password.length() < 4) {
                sendMessage("[SERVER] Password must be at least 4 characters.");
                continue;
            }

            switch (action) {
                case "REGISTER":
                    if (db.registerUser(username, password)) {
                        this.nickname = username;
                        sendMessage("AUTH:SUCCESS:Registration successful! Welcome, " + username + "!");
                        Server.log("AUTH", "New user registered: " + username + " from " + clientIP);

                        // Check for duplicate online users
                        if (Server.getClientByName(this.nickname) != null
                                && Server.getClientByName(this.nickname) != this) {
                            sendMessage("[SERVER] This account is already logged in from another location.");
                            return false;
                        }
                        return true;
                    } else {
                        sendMessage("AUTH:FAIL:Username '" + username + "' already exists. Try LOGIN instead.");
                    }
                    break;

                case "LOGIN":
                    if (db.authenticateUser(username, password)) {
                        this.nickname = username;

                        // Check for duplicate online users
                        if (Server.getClientByName(this.nickname) != null
                                && Server.getClientByName(this.nickname) != this) {
                            sendMessage("AUTH:FAIL:This account is already logged in from another location.");
                            continue;
                        }

                        sendMessage("AUTH:SUCCESS:Login successful! Welcome back, " + username + "!");
                        Server.log("AUTH", "User logged in: " + username + " from " + clientIP);
                        return true;
                    } else {
                        sendMessage("AUTH:FAIL:Invalid username or password.");
                    }
                    break;

                default:
                    sendMessage("[SERVER] Unknown auth action: " + action + ". Use LOGIN or REGISTER.");
            }
        }

        sendMessage("[SERVER] Too many failed attempts. Disconnecting.");
        Server.log("AUTH", "Authentication failed after " + maxAttempts + " attempts from " + clientIP);
        return false;
    }

    // ──────────────────────────────────────────────────────────────────
    //  Chat History
    // ──────────────────────────────────────────────────────────────────

    /**
     * Sends recent chat history to this client from the database.
     * Shows the last 20 broadcast messages so the user can catch up.
     */
    private void sendChatHistory() {
        java.util.List<String> history = DatabaseManager.getInstance().getRecentMessages(20);
        if (history.isEmpty()) {
            sendMessage("[SERVER] No chat history available yet.");
        } else {
            sendMessage("[SERVER] \u2500\u2500\u2500 Recent Chat History (" + history.size() + " messages) \u2500\u2500\u2500");
            for (String msg : history) {
                sendMessage("  " + msg);
            }
            sendMessage("[SERVER] \u2500\u2500\u2500 End of History \u2500\u2500\u2500");
        }
    }
}
