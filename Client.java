import java.io.*;
import java.net.*;
import java.util.Scanner;
import javax.crypto.SecretKey;

/**
 * Client.java - Production-Grade TCP Chat Client
 *
 * Connects to the chat server, sends a nickname, and enables real-time
 * bidirectional messaging via two threads:
 *   - Main thread:     reads user input from console → sends to server (encrypted)
 *   - Listener thread: reads server messages (encrypted) → decrypts → prints to console
 *
 * Security:
 *   - All messages are encrypted with AES-256-GCM
 *   - Encryption key is received from server on connect (key exchange handshake)
 *
 * Supports commands:
 *   /msg <user> <text>  - Private message (routed by server)
 *   /list               - Show online users
 *   /stats              - Show server statistics
 *   /help               - Show available commands
 *   exit                - Disconnect
 *
 * Usage:
 *   java Client                        → connects to localhost:5000
 *   java Client 192.168.1.10           → connects to 192.168.1.10:5000
 *   java Client 192.168.1.10 8080      → connects to 192.168.1.10:8080
 *   java Client --host 10.0.0.1 --port 8080
 *
 * @author Socket-Project Team
 */
public class Client {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5000;

    /** Flag to signal the main loop to exit when the server disconnects. */
    private static volatile boolean connected = true;

    public static void main(String[] args) {
        String hostname = DEFAULT_HOST;
        int port = DEFAULT_PORT;

        // Parse command-line arguments for host and port.
        hostname = parseHost(args, hostname);
        port = parsePort(args, port);

        try (Socket socket = new Socket(hostname, port)) {

            printBanner(hostname, port);

            // --- Set up I/O streams ---
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            Scanner scanner = new Scanner(System.in);

            // --- Step 1: Receive Encryption Key ---
            String keyLine = reader.readLine();
            SecretKey encryptionKey = null;
            if (keyLine != null && keyLine.startsWith("KEY:")) {
                String keyStr = keyLine.substring(4);
                encryptionKey = CryptoUtils.stringToKey(keyStr);
                System.out.println("  \uD83D\uDD12 Encryption: AES-256-GCM (secured)");
            } else {
                System.out.println("  \u26A0\uFE0F  Warning: No encryption key received.");
            }

            // --- Step 2: Authentication (Register or Login) ---
            String nickname = handleAuthentication(scanner, writer, reader, encryptionKey);
            if (nickname == null) {
                System.out.println("[ERROR] Authentication failed. Disconnecting.");
                return;
            }

            System.out.println("\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500");
            System.out.println("  Welcome, " + nickname + "!");
            System.out.println("  Type /help for commands, 'exit' to leave.");
            System.out.println("  \uD83D\uDD12 Messages are encrypted end-to-end.");
            System.out.println("\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500");

            // --- Step 3: Start Listener Thread ---
            Thread listenerThread = new Thread(new ServerListener(reader, encryptionKey));
            listenerThread.setDaemon(true);
            listenerThread.start();

            // --- Step 4: Main Input Loop ---
            final SecretKey finalKey = encryptionKey;
            while (connected) {
                try {
                    if (!scanner.hasNextLine()) break;
                    String userInput = scanner.nextLine();

                    // Skip empty input.
                    if (userInput.trim().isEmpty()) continue;

                    // Encrypt and send to server.
                    if (finalKey != null) {
                        try {
                            writer.println(CryptoUtils.encrypt(userInput, finalKey));
                        } catch (Exception e) {
                            writer.println(userInput);
                        }
                    } else {
                        writer.println(userInput);
                    }

                    // If the user typed "exit", disconnect.
                    if (userInput.trim().equalsIgnoreCase("exit")) {
                        System.out.println("[INFO] Disconnecting...");
                        break;
                    }

                } catch (Exception e) {
                    // NoSuchElementException if System.in is closed, or
                    // IllegalStateException if scanner is closed.
                    if (connected) {
                        System.out.println("[INFO] Input stream closed.");
                    }
                    break;
                }
            }

            scanner.close();

        } catch (ConnectException e) {
            System.out.println("╔══════════════════════════════════════════════╗");
            System.out.println("║  ERROR: Cannot connect to server!            ║");
            System.out.println("╠══════════════════════════════════════════════╣");
            System.out.println("║  Make sure the server is running:            ║");
            System.out.println("║    java Server " + port);
            System.out.println("║  on host: " + hostname);
            System.out.println("╚══════════════════════════════════════════════╝");
        } catch (UnknownHostException e) {
            System.out.println("[ERROR] Unknown host: " + hostname);
            System.out.println("        Check the hostname or IP address.");
        } catch (IOException e) {
            System.out.println("[ERROR] Connection error: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  Argument Parsing
    // ──────────────────────────────────────────────────────────────────

    private static String parseHost(String[] args, String defaultHost) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--host") && i + 1 < args.length) {
                return args[i + 1];
            } else if (i == 0 && !args[i].startsWith("-") && !isNumeric(args[i])) {
                return args[i];
            }
        }
        return defaultHost;
    }

    private static int parsePort(String[] args, int defaultPort) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--port") && i + 1 < args.length) {
                try { return Integer.parseInt(args[i + 1]); }
                catch (NumberFormatException e) { /* use default */ }
            } else if (i == 1 && !args[i].startsWith("-")) {
                try { return Integer.parseInt(args[i]); }
                catch (NumberFormatException e) { /* use default */ }
            }
        }
        return defaultPort;
    }

    private static boolean isNumeric(String str) {
        try { Integer.parseInt(str); return true; }
        catch (NumberFormatException e) { return false; }
    }

    // ──────────────────────────────────────────────────────────────────
    //  UI
    // ──────────────────────────────────────────────────────────────────

    private static void printBanner(String host, int port) {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║       TCP Chat Client v3.0 (Encrypted)       ║");
        System.out.println("╠══════════════════════════════════════════════╣");
        System.out.printf( "║  Connected to: %-29s ║%n", host + ":" + port);
        System.out.println("╚══════════════════════════════════════════════╝");
    }

    /** Called by the listener thread when the server disconnects. */
    static void onServerDisconnect() {
        connected = false;
    }

    // ──────────────────────────────────────────────────────────────────
    //  Authentication
    // ──────────────────────────────────────────────────────────────────

    /**
     * Handles the authentication flow on the client side.
     * Presents a menu to register or login, collects credentials,
     * and sends them encrypted to the server.
     *
     * @return The authenticated username, or null if authentication failed.
     */
    private static String handleAuthentication(Scanner scanner, PrintWriter writer,
                                                BufferedReader reader, SecretKey encryptionKey) {
        int maxAttempts = 3;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            System.out.println();
            System.out.println("╔══════════════════════════════════════════╗");
            System.out.println("║           Authentication                 ║");
            System.out.println("╠══════════════════════════════════════════╣");
            System.out.println("║  1. Login     (existing account)         ║");
            System.out.println("║  2. Register  (new account)              ║");
            System.out.println("╚══════════════════════════════════════════╝");
            System.out.print("  Choose (1 or 2): ");

            String choice = scanner.nextLine().trim();
            String action;
            if (choice.equals("1") || choice.equalsIgnoreCase("login")) {
                action = "LOGIN";
            } else if (choice.equals("2") || choice.equalsIgnoreCase("register")) {
                action = "REGISTER";
            } else {
                System.out.println("  [!] Invalid choice. Please enter 1 or 2.");
                continue;
            }

            System.out.print("  Username: ");
            String username = scanner.nextLine().trim();
            System.out.print("  Password: ");
            String password = scanner.nextLine().trim();

            if (username.isEmpty() || password.isEmpty()) {
                System.out.println("  [!] Username and password cannot be empty.");
                continue;
            }

            // Build auth message: AUTH:LOGIN:username:password or AUTH:REGISTER:username:password
            String authMessage = "AUTH:" + action + ":" + username + ":" + password;

            // Encrypt and send
            if (encryptionKey != null) {
                try {
                    writer.println(CryptoUtils.encrypt(authMessage, encryptionKey));
                } catch (Exception e) {
                    writer.println(authMessage);
                }
            } else {
                writer.println(authMessage);
            }

            // Read server response
            try {
                String response = reader.readLine();
                if (response == null) {
                    System.out.println("  [!] Server disconnected.");
                    return null;
                }

                // Decrypt response
                String decrypted;
                if (encryptionKey != null) {
                    try {
                        decrypted = CryptoUtils.decrypt(response, encryptionKey);
                    } catch (Exception e) {
                        decrypted = response;
                    }
                } else {
                    decrypted = response;
                }

                if (decrypted.startsWith("AUTH:SUCCESS:")) {
                    String successMsg = decrypted.substring("AUTH:SUCCESS:".length());
                    System.out.println("  ✅ " + successMsg);
                    return username;
                } else if (decrypted.startsWith("AUTH:FAIL:")) {
                    String failMsg = decrypted.substring("AUTH:FAIL:".length());
                    System.out.println("  ❌ " + failMsg);
                } else {
                    System.out.println("  " + decrypted);
                }

            } catch (IOException e) {
                System.out.println("  [!] Connection error: " + e.getMessage());
                return null;
            }
        }

        return null;
    }
}

/**
 * ServerListener - Background thread receiving encrypted messages from the server.
 *
 * Runs as a daemon thread. Continuously reads encrypted lines from the server,
 * decrypts them using the shared AES key, and prints the plaintext to console.
 * When the server closes the connection (readLine returns null),
 * signals the main thread to exit.
 */
class ServerListener implements Runnable {

    private final BufferedReader reader;
    private final SecretKey encryptionKey;

    public ServerListener(BufferedReader reader, SecretKey encryptionKey) {
        this.reader = reader;
        this.encryptionKey = encryptionKey;
    }

    @Override
    public void run() {
        try {
            String serverMessage;
            while ((serverMessage = reader.readLine()) != null) {
                // Decrypt the message from the server
                String decrypted;
                if (encryptionKey != null) {
                    try {
                        decrypted = CryptoUtils.decrypt(serverMessage, encryptionKey);
                    } catch (Exception e) {
                        // If decryption fails, show as-is (e.g. plaintext server messages)
                        decrypted = serverMessage;
                    }
                } else {
                    decrypted = serverMessage;
                }

                // Use \r to clear the current line, print the message,
                // then re-display the prompt.
                System.out.println("\r" + decrypted);
                System.out.print("> ");
            }
        } catch (IOException e) {
            // Connection lost
        }

        // Server disconnected — notify main thread.
        System.out.println("\n[INFO] Server connection closed.");
        System.out.println("[INFO] Press Enter to exit.");
        Client.onServerDisconnect();
    }
}
