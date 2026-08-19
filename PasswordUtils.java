import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * PasswordUtils.java - Secure Password Hashing Utility
 *
 * Provides password hashing and verification using SHA-256 with per-user
 * random salt. This prevents rainbow table attacks and ensures identical
 * passwords produce different hashes for different users.
 *
 * Security features:
 *   - SHA-256 hashing (256-bit digest)
 *   - Random 16-byte salt per user (prevents rainbow table attacks)
 *   - Constant-time comparison for hash verification (prevents timing attacks)
 *   - Salt stored alongside hash in format: salt$hash
 *
 * Storage format:
 *   Base64(salt) + "$" + Base64(hash)
 *
 * Usage:
 *   String hashed = PasswordUtils.hashPassword("myPassword123");
 *   boolean valid = PasswordUtils.verifyPassword("myPassword123", hashed);
 *
 * @author Socket-Project Team
 */
public class PasswordUtils {

    // ──────────────────────────────────────────────────────────────────
    //  Constants
    // ──────────────────────────────────────────────────────────────────

    /** Salt length in bytes (128 bits of randomness). */
    private static final int SALT_LENGTH = 16;

    /** Hashing algorithm. */
    private static final String HASH_ALGORITHM = "SHA-256";

    /** Separator between salt and hash in the stored format. */
    private static final String SEPARATOR = "$";

    /** Cryptographically secure random number generator for salt generation. */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ──────────────────────────────────────────────────────────────────
    //  Public API
    // ──────────────────────────────────────────────────────────────────

    /**
     * Hashes a password with a random salt using SHA-256.
     *
     * Process:
     *   1. Generate a random 16-byte salt
     *   2. Concatenate salt + password bytes
     *   3. Compute SHA-256 hash
     *   4. Return Base64(salt) + "$" + Base64(hash)
     *
     * @param password The plaintext password to hash.
     * @return A string in format "salt$hash" suitable for database storage.
     * @throws RuntimeException if SHA-256 is not available (should never happen).
     */
    public static String hashPassword(String password) {
        try {
            // Step 1: Generate random salt
            byte[] salt = new byte[SALT_LENGTH];
            SECURE_RANDOM.nextBytes(salt);

            // Step 2: Hash password with salt
            byte[] hash = computeHash(password, salt);

            // Step 3: Encode both as Base64 and combine
            String saltBase64 = Base64.getEncoder().encodeToString(salt);
            String hashBase64 = Base64.getEncoder().encodeToString(hash);

            return saltBase64 + SEPARATOR + hashBase64;

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Verifies a password against a previously stored hash.
     *
     * Process:
     *   1. Extract the salt from the stored hash string
     *   2. Hash the candidate password with the same salt
     *   3. Compare the computed hash with the stored hash
     *   4. Use constant-time comparison to prevent timing attacks
     *
     * @param password   The plaintext password to verify.
     * @param storedHash The stored hash string in "salt$hash" format.
     * @return true if the password matches, false otherwise.
     */
    public static boolean verifyPassword(String password, String storedHash) {
        try {
            // Split stored hash into salt and hash components
            String[] parts = storedHash.split("\\" + SEPARATOR);
            if (parts.length != 2) {
                return false;
            }

            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[1]);

            // Compute hash with the extracted salt
            byte[] actualHash = computeHash(password, salt);

            // Constant-time comparison (prevents timing attacks)
            return constantTimeEquals(expectedHash, actualHash);

        } catch (Exception e) {
            return false;
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  Internal Methods
    // ──────────────────────────────────────────────────────────────────

    /**
     * Computes SHA-256 hash of (salt + password).
     *
     * @param password The plaintext password.
     * @param salt     The random salt bytes.
     * @return The SHA-256 hash bytes.
     */
    private static byte[] computeHash(String password, byte[] salt) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
        digest.update(salt);
        return digest.digest(password.getBytes());
    }

    /**
     * Constant-time byte array comparison.
     * Prevents timing attacks by always comparing ALL bytes regardless
     * of where the first difference occurs.
     *
     * @param a First byte array.
     * @param b Second byte array.
     * @return true if arrays are equal, false otherwise.
     */
    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;

        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }
}
