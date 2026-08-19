import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * DatabaseManager.java - SQLite Database Manager for Chat Application
 *
 * Manages persistent storage for user accounts and chat message history
 * using an embedded SQLite database (no external server required).
 *
 * Database schema:
 *   - users:    id, username, password_hash, created_at, last_login
 *   - messages: id, sender, message, type, target, timestamp
 *
 * Features:
 *   - User registration with hashed passwords (via PasswordUtils)
 *   - User authentication (login verification)
 *   - Chat history persistence and retrieval
 *   - Thread-safe singleton pattern with synchronized methods
 *   - Auto-creation of database and tables on first run
 *
 * Dependencies:
 *   - SQLite JDBC driver (sqlite-jdbc-*.jar)
 *   - PasswordUtils.java for password hashing
 *
 * Usage:
 *   DatabaseManager db = DatabaseManager.getInstance();
 *   db.registerUser("alice", "password123");
 *   boolean valid = db.authenticateUser("alice", "password123");
 *   db.saveMessage("alice", "Hello everyone!", "BROADCAST", null);
 *   List<String> history = db.getRecentMessages(50);
 *
 * @author Socket-Project Team
 */
public class DatabaseManager {

    // ──────────────────────────────────────────────────────────────────
    //  Constants
    // ──────────────────────────────────────────────────────────────────

    /** SQLite database file name (created in the project root). */
    private static final String DB_FILE = "chat_server.db";

    /** JDBC connection URL for the SQLite database. */
    private static final String DB_URL = "jdbc:sqlite:" + DB_FILE;

    /** Date format for timestamps in the database. */
    private static final DateTimeFormatter DB_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ──────────────────────────────────────────────────────────────────
    //  Singleton
    // ──────────────────────────────────────────────────────────────────

    /** Singleton instance. */
    private static DatabaseManager instance;

    /** The database connection (reused for performance). */
    private Connection connection;

    /**
     * Returns the singleton DatabaseManager instance.
     * Creates it (and the database) on first access.
     *
     * @return The DatabaseManager instance.
     */
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /**
     * Private constructor — initializes the database connection and creates
     * tables if they don't exist.
     */
    private DatabaseManager() {
        try {
            // Load SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(DB_URL);

            // Enable WAL mode for better concurrent read/write performance
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL;");
            }

            createTables();
            Server.log("DB", "SQLite database initialized: " + DB_FILE);

        } catch (ClassNotFoundException e) {
            Server.log("ERROR", "SQLite JDBC driver not found. Make sure sqlite-jdbc.jar is in classpath.");
            Server.log("ERROR", "Download from: https://github.com/xerial/sqlite-jdbc/releases");
        } catch (SQLException e) {
            Server.log("ERROR", "Database initialization failed: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  Schema Creation
    // ──────────────────────────────────────────────────────────────────

    /**
     * Creates the database tables if they don't already exist.
     *
     * Tables:
     *   - users: stores registered user accounts with hashed passwords
     *   - messages: stores all chat messages (broadcast, PM, system)
     */
    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {

            // Users table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS users (" +
                "  id          INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  username    TEXT    NOT NULL UNIQUE," +
                "  password_hash TEXT NOT NULL," +
                "  created_at  TEXT    NOT NULL," +
                "  last_login  TEXT" +
                ")"
            );

            // Messages table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS messages (" +
                "  id        INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  sender    TEXT    NOT NULL," +
                "  message   TEXT    NOT NULL," +
                "  type      TEXT    NOT NULL DEFAULT 'BROADCAST'," +
                "  target    TEXT," +
                "  timestamp TEXT    NOT NULL" +
                ")"
            );

            // Index for faster message retrieval
            stmt.execute(
                "CREATE INDEX IF NOT EXISTS idx_messages_timestamp ON messages(timestamp)"
            );

            // Index for faster user lookup
            stmt.execute(
                "CREATE INDEX IF NOT EXISTS idx_users_username ON users(username)"
            );
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  User Management
    // ──────────────────────────────────────────────────────────────────

    /**
     * Registers a new user with a hashed password.
     *
     * @param username The desired username.
     * @param password The plaintext password (will be hashed before storage).
     * @return true if registration succeeded, false if the username already exists.
     */
    public synchronized boolean registerUser(String username, String password) {
        if (connection == null) return false;

        try {
            // Check if username already exists
            if (userExists(username)) {
                return false;
            }

            String sql = "INSERT INTO users (username, password_hash, created_at) VALUES (?, ?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, username.toLowerCase());
                pstmt.setString(2, PasswordUtils.hashPassword(password));
                pstmt.setString(3, LocalDateTime.now().format(DB_TIME_FMT));
                pstmt.executeUpdate();
                Server.log("DB", "New user registered: " + username);
                return true;
            }

        } catch (SQLException e) {
            Server.log("ERROR", "Registration failed for " + username + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Authenticates a user by verifying their password.
     *
     * @param username The username to authenticate.
     * @param password The plaintext password to verify.
     * @return true if the credentials are valid, false otherwise.
     */
    public synchronized boolean authenticateUser(String username, String password) {
        if (connection == null) return false;

        try {
            String sql = "SELECT password_hash FROM users WHERE username = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, username.toLowerCase());
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    boolean valid = PasswordUtils.verifyPassword(password, storedHash);

                    if (valid) {
                        // Update last login time
                        updateLastLogin(username);
                    }

                    return valid;
                }
            }
        } catch (SQLException e) {
            Server.log("ERROR", "Authentication error for " + username + ": " + e.getMessage());
        }
        return false;
    }

    /**
     * Checks if a username already exists in the database.
     *
     * @param username The username to check.
     * @return true if the user exists, false otherwise.
     */
    public synchronized boolean userExists(String username) {
        if (connection == null) return false;

        try {
            String sql = "SELECT COUNT(*) FROM users WHERE username = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, username.toLowerCase());
                ResultSet rs = pstmt.executeQuery();
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Updates the last login timestamp for a user.
     *
     * @param username The username to update.
     */
    private void updateLastLogin(String username) {
        try {
            String sql = "UPDATE users SET last_login = ? WHERE username = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, LocalDateTime.now().format(DB_TIME_FMT));
                pstmt.setString(2, username.toLowerCase());
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            Server.log("ERROR", "Failed to update last login for " + username);
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  Message History
    // ──────────────────────────────────────────────────────────────────

    /**
     * Saves a chat message to the database.
     *
     * @param sender  The nickname of the message sender.
     * @param message The message content.
     * @param type    Message type: "BROADCAST", "PM", or "SYSTEM".
     * @param target  The target user (for PM), or null for broadcast/system.
     */
    public synchronized void saveMessage(String sender, String message, String type, String target) {
        if (connection == null) return;

        try {
            String sql = "INSERT INTO messages (sender, message, type, target, timestamp) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, sender);
                pstmt.setString(2, message);
                pstmt.setString(3, type);
                pstmt.setString(4, target);
                pstmt.setString(5, LocalDateTime.now().format(DB_TIME_FMT));
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            Server.log("ERROR", "Failed to save message from " + sender + ": " + e.getMessage());
        }
    }

    /**
     * Retrieves the most recent broadcast messages from the database.
     * Used to show chat history to a user when they join.
     *
     * @param limit Maximum number of messages to retrieve.
     * @return List of formatted message strings, oldest first.
     */
    public synchronized List<String> getRecentMessages(int limit) {
        List<String> messages = new ArrayList<>();
        if (connection == null) return messages;

        try {
            String sql = "SELECT sender, message, timestamp FROM messages " +
                         "WHERE type = 'BROADCAST' " +
                         "ORDER BY id DESC LIMIT ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setInt(1, limit);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    String line = String.format("[%s] [%s]: %s",
                            rs.getString("timestamp"),
                            rs.getString("sender"),
                            rs.getString("message"));
                    messages.add(0, line); // Add to front to reverse chronological order
                }
            }
        } catch (SQLException e) {
            Server.log("ERROR", "Failed to retrieve message history: " + e.getMessage());
        }

        return messages;
    }

    /**
     * Retrieves private messages between two users.
     *
     * @param user1 First user's nickname.
     * @param user2 Second user's nickname.
     * @param limit Maximum number of messages to retrieve.
     * @return List of formatted PM strings, oldest first.
     */
    public synchronized List<String> getPrivateMessages(String user1, String user2, int limit) {
        List<String> messages = new ArrayList<>();
        if (connection == null) return messages;

        try {
            String sql = "SELECT sender, message, target, timestamp FROM messages " +
                         "WHERE type = 'PM' AND " +
                         "((sender = ? AND target = ?) OR (sender = ? AND target = ?)) " +
                         "ORDER BY id DESC LIMIT ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, user1.toLowerCase());
                pstmt.setString(2, user2.toLowerCase());
                pstmt.setString(3, user2.toLowerCase());
                pstmt.setString(4, user1.toLowerCase());
                pstmt.setInt(5, limit);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    String line = String.format("[%s] [PM %s → %s]: %s",
                            rs.getString("timestamp"),
                            rs.getString("sender"),
                            rs.getString("target"),
                            rs.getString("message"));
                    messages.add(0, line);
                }
            }
        } catch (SQLException e) {
            Server.log("ERROR", "Failed to retrieve PM history: " + e.getMessage());
        }

        return messages;
    }

    /**
     * Returns total number of messages stored in the database.
     *
     * @return The total message count.
     */
    public synchronized int getTotalMessageCount() {
        if (connection == null) return 0;

        try {
            String sql = "SELECT COUNT(*) FROM messages";
            try (Statement stmt = connection.createStatement()) {
                ResultSet rs = stmt.executeQuery(sql);
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            // ignore
        }
        return 0;
    }

    /**
     * Returns total number of registered users.
     *
     * @return The total user count.
     */
    public synchronized int getTotalUserCount() {
        if (connection == null) return 0;

        try {
            String sql = "SELECT COUNT(*) FROM users";
            try (Statement stmt = connection.createStatement()) {
                ResultSet rs = stmt.executeQuery(sql);
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            // ignore
        }
        return 0;
    }

    // ──────────────────────────────────────────────────────────────────
    //  Cleanup
    // ──────────────────────────────────────────────────────────────────

    /**
     * Closes the database connection. Should be called during server shutdown.
     */
    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                Server.log("DB", "Database connection closed.");
            }
        } catch (SQLException e) {
            Server.log("ERROR", "Error closing database: " + e.getMessage());
        }
    }
}
