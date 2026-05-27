import java.io.*;
import java.net.*;
import java.util.Scanner;

/**
 * Client.java - Production-Grade TCP Chat Client
 *
 * Connects to the chat server, sends a nickname, and enables real-time
 * bidirectional messaging via two threads:
 *   - Main thread:     reads user input from console → sends to server
 *   - Listener thread: reads server messages → prints to console
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

            // --- Step 1: Send Nickname ---
            System.out.print("Enter your nickname: ");
            String nickname = scanner.nextLine().trim();
            if (nickname.isEmpty()) {
                nickname = "User" + (int) (Math.random() * 9999);
            }
            writer.println(nickname);

            System.out.println("───────────────────────────────────────────");
            System.out.println("  Welcome, " + nickname + "!");
            System.out.println("  Type /help for commands, 'exit' to leave.");
            System.out.println("───────────────────────────────────────────");

            // --- Step 2: Start Listener Thread ---
            Thread listenerThread = new Thread(new ServerListener(reader));
            listenerThread.setDaemon(true);
            listenerThread.start();

            // --- Step 3: Main Input Loop ---
            while (connected) {
                try {
                    if (!scanner.hasNextLine()) break;
                    String userInput = scanner.nextLine();

                    // Skip empty input.
                    if (userInput.trim().isEmpty()) continue;

                    // Send to server.
                    writer.println(userInput);

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
        System.out.println("║       TCP Chat Client v2.0                   ║");
        System.out.println("╠══════════════════════════════════════════════╣");
        System.out.printf( "║  Connected to: %-29s ║%n", host + ":" + port);
        System.out.println("╚══════════════════════════════════════════════╝");
    }

    /** Called by the listener thread when the server disconnects. */
    static void onServerDisconnect() {
        connected = false;
    }
}

/**
 * ServerListener - Background thread receiving messages from the server.
 *
 * Runs as a daemon thread. Continuously reads lines from the server and
 * prints them to the console. When the server closes the connection
 * (readLine returns null), signals the main thread to exit.
 */
class ServerListener implements Runnable {

    private final BufferedReader reader;

    public ServerListener(BufferedReader reader) {
        this.reader = reader;
    }

    @Override
    public void run() {
        try {
            String serverMessage;
            while ((serverMessage = reader.readLine()) != null) {
                // Use \r to clear the current line, print the message,
                // then re-display the prompt.
                System.out.println("\r" + serverMessage);
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
