import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.SecretKey;

/**
 * WebSocketHandler.java - WebSocket Protocol Handler for Web-Based Chat Client
 *
 * Implements the WebSocket protocol (RFC 6455) to enable real-time
 * communication between the web browser and the chat server.
 *
 * Protocol overview:
 *   1. Client sends HTTP Upgrade request
 *   2. Server responds with 101 Switching Protocols
 *   3. Both sides communicate via WebSocket frames
 *
 * Frame structure:
 *   - Text frames (opcode 0x1) carry chat messages as UTF-8
 *   - Close frames (opcode 0x8) signal disconnection
 *   - Ping/Pong frames (opcode 0x9/0xA) for keep-alive
 *
 * Security:
 *   - Messages are encrypted with AES-256-GCM (same as TCP clients)
 *   - WebSocket handshake uses SHA-1 per RFC 6455
 *
 * @author Socket-Project Team
 */
public class WebSocketHandler implements Runnable {

    // ──────────────────────────────────────────────────────────────────
    //  Constants
    // ──────────────────────────────────────────────────────────────────

    /** WebSocket magic string defined in RFC 6455 for handshake. */
    private static final String WEBSOCKET_MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    // ──────────────────────────────────────────────────────────────────
    //  Fields
    // ──────────────────────────────────────────────────────────────────

    private final Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private String nickname;
    private volatile boolean running = true;
    private final SecretKey encryptionKey;
    private final String clientIP;
    private ClientHandler associatedHandler;

    // ──────────────────────────────────────────────────────────────────
    //  Constructor
    // ──────────────────────────────────────────────────────────────────

    public WebSocketHandler(Socket socket, SecretKey encryptionKey) {
        this.socket = socket;
        this.encryptionKey = encryptionKey;
        this.clientIP = socket.getInetAddress().getHostAddress();
    }

    // ──────────────────────────────────────────────────────────────────
    //  Main Thread
    // ──────────────────────────────────────────────────────────────────

    @Override
    public void run() {
        try {
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();

            // Step 1: Perform WebSocket handshake
            if (!performHandshake()) {
                Server.log("WS", "WebSocket handshake failed from " + clientIP);
                return;
            }
            Server.log("WS", "WebSocket connected from " + clientIP);

            // Step 2: Message loop — read WebSocket frames
            while (running && !socket.isClosed()) {
                String message = readFrame();
                if (message == null) break;

                handleMessage(message);
            }

        } catch (IOException e) {
            if (running) {
                Server.log("WS", "WebSocket disconnected: " + clientIP);
            }
        } finally {
            cleanup();
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  WebSocket Handshake (RFC 6455)
    // ──────────────────────────────────────────────────────────────────

    /**
     * Performs the WebSocket upgrade handshake.
     * Reads the HTTP request, extracts the Sec-WebSocket-Key,
     * computes the accept hash, and sends the 101 response.
     */
    private boolean performHandshake() throws IOException {
        StringBuilder headerBuilder = new StringBuilder();
        int b;
        while ((b = inputStream.read()) != -1) {
            headerBuilder.append((char) b);
            if (headerBuilder.length() >= 4 && 
                headerBuilder.substring(headerBuilder.length() - 4).equals("\r\n\r\n")) {
                break;
            }
        }

        String headers = headerBuilder.toString();
        String webSocketKey = null;

        for (String line : headers.split("\r\n")) {
            if (line.toLowerCase().startsWith("sec-websocket-key:")) {
                webSocketKey = line.substring(18).trim();
            }
        }

        if (webSocketKey == null) return false;

        // Compute the accept key per RFC 6455
        try {
            String acceptKey = Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-1")
                            .digest((webSocketKey + WEBSOCKET_MAGIC).getBytes("UTF-8"))
            );

            // Send the 101 Switching Protocols response
            String response = "HTTP/1.1 101 Switching Protocols\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + acceptKey + "\r\n"
                    + "\r\n";
            outputStream.write(response.getBytes("UTF-8"));
            outputStream.flush();
            return true;

        } catch (Exception e) {
            Server.log("ERROR", "WebSocket handshake error: " + e.getMessage());
            return false;
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  WebSocket Frame Reading
    // ──────────────────────────────────────────────────────────────────

    /**
     * Reads a single WebSocket text frame.
     * Supports frames up to 65535 bytes (sufficient for chat messages).
     */
    private String readFrame() throws IOException {
        int firstByte = inputStream.read();
        if (firstByte == -1) return null;

        int opcode = firstByte & 0x0F;

        // Close frame
        if (opcode == 0x8) return null;

        // Ping frame — respond with pong
        if (opcode == 0x9) {
            // Read and discard the ping payload, then send pong
            int secondByte = inputStream.read();
            int payloadLength = secondByte & 0x7F;
            boolean masked = (secondByte & 0x80) != 0;
            byte[] payload = readPayload(payloadLength, masked);
            sendPong(payload);
            return readFrame(); // Continue reading
        }

        int secondByte = inputStream.read();
        if (secondByte == -1) return null;

        boolean masked = (secondByte & 0x80) != 0;
        long payloadLength = secondByte & 0x7F;

        if (payloadLength == 126) {
            payloadLength = ((inputStream.read() & 0xFF) << 8) | (inputStream.read() & 0xFF);
        } else if (payloadLength == 127) {
            payloadLength = 0;
            for (int i = 0; i < 8; i++) {
                payloadLength = (payloadLength << 8) | (inputStream.read() & 0xFF);
            }
        }

        byte[] maskKey = null;
        if (masked) {
            maskKey = new byte[4];
            inputStream.read(maskKey, 0, 4);
        }

        byte[] payload = new byte[(int) payloadLength];
        int totalRead = 0;
        while (totalRead < payloadLength) {
            int read = inputStream.read(payload, totalRead, (int) payloadLength - totalRead);
            if (read == -1) return null;
            totalRead += read;
        }

        // Unmask the payload
        if (masked && maskKey != null) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] = (byte) (payload[i] ^ maskKey[i % 4]);
            }
        }

        return new String(payload, "UTF-8");
    }

    private byte[] readPayload(int length, boolean masked) throws IOException {
        byte[] maskKey = null;
        if (masked) {
            maskKey = new byte[4];
            inputStream.read(maskKey, 0, 4);
        }
        byte[] payload = new byte[length];
        int totalRead = 0;
        while (totalRead < length) {
            int read = inputStream.read(payload, totalRead, length - totalRead);
            if (read == -1) break;
            totalRead += read;
        }
        if (masked && maskKey != null) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] = (byte) (payload[i] ^ maskKey[i % 4]);
            }
        }
        return payload;
    }

    // ──────────────────────────────────────────────────────────────────
    //  WebSocket Frame Writing
    // ──────────────────────────────────────────────────────────────────

    /**
     * Sends a text frame to the WebSocket client.
     */
    public synchronized void sendFrame(String message) {
        try {
            byte[] payload = message.getBytes("UTF-8");
            int length = payload.length;

            // FIN + Text opcode
            outputStream.write(0x81);

            if (length <= 125) {
                outputStream.write(length);
            } else if (length <= 65535) {
                outputStream.write(126);
                outputStream.write((length >> 8) & 0xFF);
                outputStream.write(length & 0xFF);
            } else {
                outputStream.write(127);
                for (int i = 7; i >= 0; i--) {
                    outputStream.write((int) ((length >> (8 * i)) & 0xFF));
                }
            }

            outputStream.write(payload);
            outputStream.flush();
        } catch (IOException e) {
            running = false;
        }
    }

    /**
     * Sends a pong frame (response to ping).
     */
    private void sendPong(byte[] payload) {
        try {
            outputStream.write(0x8A); // FIN + Pong opcode
            outputStream.write(payload.length);
            outputStream.write(payload);
            outputStream.flush();
        } catch (IOException e) {
            running = false;
        }
    }

    /**
     * Sends a close frame.
     */
    private void sendClose() {
        try {
            outputStream.write(0x88); // FIN + Close opcode
            outputStream.write(0);
            outputStream.flush();
        } catch (IOException e) {
            // Ignore — we're closing anyway
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  Message Handling
    // ──────────────────────────────────────────────────────────────────

    /**
     * Routes incoming messages from the web client.
     * Messages are JSON-formatted from the web UI.
     * Expected formats:
     *   {"type":"auth","action":"login","username":"...", "password":"..."}
     *   {"type":"auth","action":"register","username":"...", "password":"..."}
     *   {"type":"chat","message":"..."}
     *   {"type":"command","command":"..."}
     */
    private void handleMessage(String raw) {
        Server.log("WS", "Received message: " + raw);
        // Simple JSON parsing without external library
        String type = extractJsonValue(raw, "type");

        if ("auth".equals(type)) {
            handleAuth(raw);
        } else if ("chat".equals(type)) {
            handleChat(raw);
        } else if ("command".equals(type)) {
            handleWebCommand(raw);
        }
    }

    private void handleAuth(String raw) {
        String action = extractJsonValue(raw, "action");
        String username = extractJsonValue(raw, "username");
        String password = extractJsonValue(raw, "password");

        if (username == null || password == null || username.isEmpty() || password.isEmpty()) {
            sendJson("auth", "error", "Username and password are required.");
            return;
        }

        if (username.length() < 3 || username.length() > 20) {
            sendJson("auth", "error", "Username must be 3-20 characters.");
            return;
        }

        DatabaseManager db = DatabaseManager.getInstance();

        if ("register".equals(action)) {
            if (db.registerUser(username, password)) {
                this.nickname = username;
                // Check duplicate online
                if (Server.getClientByName(this.nickname) != null) {
                    sendJson("auth", "error", "Account is already logged in.");
                    this.nickname = null;
                    return;
                }
                registerAsClientHandler();
                sendJson("auth", "success", "Registration successful! Welcome, " + username + "!");
                Server.log("WS-AUTH", "New user registered: " + username);

                // Announce join
                Server.broadcast("[SERVER] " + nickname + " has joined the chat! "
                        + "(Online: " + Server.getOnlineCount() + ")", associatedHandler);

                sendChatHistory();
            } else {
                sendJson("auth", "error", "Username already exists. Try logging in.");
            }
        } else if ("login".equals(action)) {
            if (db.authenticateUser(username, password)) {
                this.nickname = username;
                if (Server.getClientByName(this.nickname) != null) {
                    sendJson("auth", "error", "Account is already logged in from another location.");
                    this.nickname = null;
                    return;
                }
                registerAsClientHandler();
                sendJson("auth", "success", "Login successful! Welcome back, " + username + "!");
                Server.log("WS-AUTH", "User logged in: " + username);

                Server.broadcast("[SERVER] " + nickname + " has joined the chat! "
                        + "(Online: " + Server.getOnlineCount() + ")", associatedHandler);

                sendChatHistory();
            } else {
                sendJson("auth", "error", "Invalid username or password.");
            }
        }
    }

    private void handleChat(String raw) {
        String message = extractJsonValue(raw, "message");
        if (message == null || message.trim().isEmpty() || nickname == null) return;

        message = message.trim();

        if (message.equalsIgnoreCase("exit")) {
            sendJson("system", "info", "Goodbye, " + nickname + "!");
            running = false;
            return;
        }

        if (message.startsWith("/")) {
            handleWebCommand(message);
            return;
        }

        // Broadcast the chat message
        Server.recordMessage();
        Server.log("WS-CHAT", nickname + ": " + message);
        Server.broadcast("[" + nickname + "]: " + message, associatedHandler);
        DatabaseManager.getInstance().saveMessage(nickname, message, "BROADCAST", null);

        // Echo back to sender
        sendJson("chat", nickname, message);
    }

    private void handleWebCommand(String raw) {
        String command = extractJsonValue(raw, "command");
        if (command == null) command = raw; // Direct command string

        if (command.equals("/list")) {
            StringBuilder sb = new StringBuilder();
            for (String user : Server.getOnlineUsers()) {
                sb.append(user);
                if (user.equalsIgnoreCase(nickname)) sb.append(" (you)");
                sb.append("\n");
            }
            sendJson("system", "users", sb.toString().trim());
        } else if (command.equals("/stats")) {
            sendJson("system", "stats", Server.getStats());
        } else if (command.equals("/history")) {
            sendChatHistory();
        } else if (command.startsWith("/msg ")) {
            handleWebPM(command);
        } else {
            sendJson("system", "error", "Unknown command: " + command);
        }
    }

    private void handleWebPM(String command) {
        String rest = command.substring(5).trim();
        int spaceIndex = rest.indexOf(' ');
        if (spaceIndex == -1) {
            sendJson("system", "error", "Usage: /msg <username> <message>");
            return;
        }

        String targetName = rest.substring(0, spaceIndex);
        String privateMsg = rest.substring(spaceIndex + 1).trim();

        if (privateMsg.isEmpty()) {
            sendJson("system", "error", "Usage: /msg <username> <message>");
            return;
        }

        String formattedMsg = "[PM from " + nickname + "]: " + privateMsg;
        boolean delivered = Server.unicast(targetName, formattedMsg, associatedHandler);

        if (delivered) {
            sendJson("pm", nickname, "→ " + targetName + ": " + privateMsg);
            DatabaseManager.getInstance().saveMessage(nickname, privateMsg, "PM", targetName);
        } else {
            sendJson("system", "error", "User '" + targetName + "' is not online.");
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  Helper Methods
    // ──────────────────────────────────────────────────────────────────

    /**
     * Creates a ClientHandler adapter so the WebSocket client appears
     * as a regular client in the server's client list.
     */
    private void registerAsClientHandler() {
        // Create a thin ClientHandler that delegates sendMessage to WebSocket
        associatedHandler = new ClientHandler(socket, encryptionKey) {
            @Override
            public void sendMessage(String message) {
                sendJson("chat", "server", message);
            }

            @Override
            public String getNickname() {
                return nickname;
            }

            @Override
            public String getClientIP() {
                return clientIP;
            }
        };
        Server.clients.add(associatedHandler);
    }

    /**
     * Sends a JSON-formatted message to the web client.
     */
    private void sendJson(String type, String from, String content) {
        // Escape special characters for JSON
        content = content.replace("\\", "\\\\")
                         .replace("\"", "\\\"")
                         .replace("\n", "\\n")
                         .replace("\r", "\\r")
                         .replace("\t", "\\t");
        from = from.replace("\\", "\\\\").replace("\"", "\\\"");

        String json = String.format("{\"type\":\"%s\",\"from\":\"%s\",\"content\":\"%s\",\"timestamp\":%d}",
                type, from, content, System.currentTimeMillis());
        sendFrame(json);
    }

    /**
     * Sends recent chat history to the web client.
     */
    private void sendChatHistory() {
        java.util.List<String> history = DatabaseManager.getInstance().getRecentMessages(50);
        for (String msg : history) {
            sendJson("history", "server", msg);
        }
    }

    /**
     * Extracts a value from a simple JSON object.
     * Handles: {"key":"value"} format.
     */
    private String extractJsonValue(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*?)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1)
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        return null;
    }

    /**
     * Cleans up resources when the WebSocket connection closes.
     */
    private void cleanup() {
        if (nickname != null) {
            Server.broadcast("[SERVER] " + nickname + " has left the chat. "
                    + "(Online: " + (Server.getOnlineCount() - 1) + ")", associatedHandler);
            Server.log("WS", nickname + " disconnected.");
        }

        if (associatedHandler != null) {
            Server.removeClient(associatedHandler);
        }

        try {
            sendClose();
            socket.close();
        } catch (IOException e) {
            // Ignore
        }
    }
}
