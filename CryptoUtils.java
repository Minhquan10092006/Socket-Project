import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * CryptoUtils.java - AES-256-GCM Encryption/Decryption Utility
 *
 * Provides symmetric encryption for chat messages using AES-256 in GCM mode.
 * GCM (Galois/Counter Mode) provides both confidentiality AND integrity/authentication,
 * which is stronger than basic AES-CBC.
 *
 * Security features:
 *   - AES-256 bit key (military-grade encryption strength)
 *   - GCM mode with 128-bit authentication tag (AEAD - Authenticated Encryption)
 *   - Random 12-byte IV (Initialization Vector) per encryption to prevent pattern analysis
 *   - Base64 encoding for safe transmission over text-based socket streams
 *
 * Message format over the wire:
 *   Base64( IV[12 bytes] + CipherText + AuthTag[16 bytes] )
 *
 * Usage:
 *   // Generate or load a shared secret key
 *   SecretKey key = CryptoUtils.generateKey();
 *   String keyStr = CryptoUtils.keyToString(key);
 *
 *   // Encrypt
 *   String encrypted = CryptoUtils.encrypt("Hello, World!", key);
 *
 *   // Decrypt
 *   String decrypted = CryptoUtils.decrypt(encrypted, key);
 *
 * @author Socket-Project Team
 */
public class CryptoUtils {

    // ──────────────────────────────────────────────────────────────────
    //  Constants
    // ──────────────────────────────────────────────────────────────────

    /** AES algorithm identifier */
    private static final String ALGORITHM = "AES";

    /** AES in GCM mode with no padding (GCM handles its own padding) */
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    /** AES key size in bits (256-bit = military-grade) */
    private static final int KEY_SIZE = 256;

    /** GCM Initialization Vector size in bytes (NIST recommended: 12 bytes) */
    private static final int IV_SIZE = 12;

    /** GCM authentication tag length in bits (128-bit for maximum security) */
    private static final int TAG_LENGTH = 128;

    /** Cryptographically secure random number generator */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ──────────────────────────────────────────────────────────────────
    //  Key Management
    // ──────────────────────────────────────────────────────────────────

    /**
     * Generates a new random AES-256 secret key.
     *
     * @return A new SecretKey suitable for AES-256-GCM encryption.
     * @throws Exception if the AES key generator is not available.
     */
    public static SecretKey generateKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
        keyGen.init(KEY_SIZE, SECURE_RANDOM);
        return keyGen.generateKey();
    }

    /**
     * Converts a SecretKey to a Base64-encoded string for storage or transmission.
     *
     * @param key The secret key to serialize.
     * @return Base64-encoded string representation of the key.
     */
    public static String keyToString(SecretKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /**
     * Reconstructs a SecretKey from its Base64-encoded string representation.
     *
     * @param keyStr The Base64-encoded key string.
     * @return The reconstructed SecretKey.
     */
    public static SecretKey stringToKey(String keyStr) {
        byte[] decodedKey = Base64.getDecoder().decode(keyStr);
        return new SecretKeySpec(decodedKey, 0, decodedKey.length, ALGORITHM);
    }

    // ──────────────────────────────────────────────────────────────────
    //  Encryption / Decryption
    // ──────────────────────────────────────────────────────────────────

    /**
     * Encrypts a plaintext message using AES-256-GCM.
     *
     * Process:
     *   1. Generate a random 12-byte IV (prevents identical plaintexts from
     *      producing identical ciphertexts)
     *   2. Initialize the AES-GCM cipher with the key and IV
     *   3. Encrypt the plaintext bytes
     *   4. Prepend the IV to the ciphertext (needed for decryption)
     *   5. Base64-encode the result for safe text transmission
     *
     * @param plaintext The message to encrypt.
     * @param key       The AES-256 secret key.
     * @return Base64-encoded string containing IV + ciphertext + auth tag.
     * @throws Exception if encryption fails.
     */
    public static String encrypt(String plaintext, SecretKey key) throws Exception {
        // Step 1: Generate a fresh random IV for this message
        byte[] iv = new byte[IV_SIZE];
        SECURE_RANDOM.nextBytes(iv);

        // Step 2: Initialize cipher in ENCRYPT mode with GCM parameters
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);

        // Step 3: Encrypt the plaintext
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes("UTF-8"));

        // Step 4: Combine IV + ciphertext into a single byte array
        // Format: [IV (12 bytes)][Ciphertext + GCM Auth Tag]
        byte[] combined = new byte[IV_SIZE + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, IV_SIZE);
        System.arraycopy(ciphertext, 0, combined, IV_SIZE, ciphertext.length);

        // Step 5: Base64 encode for text-safe transmission over the socket
        return Base64.getEncoder().encodeToString(combined);
    }

    /**
     * Decrypts a Base64-encoded AES-256-GCM ciphertext back to plaintext.
     *
     * Process:
     *   1. Base64-decode the input
     *   2. Extract the 12-byte IV from the beginning
     *   3. Extract the ciphertext (remainder)
     *   4. Initialize the cipher in DECRYPT mode with the extracted IV
     *   5. Decrypt and verify the authentication tag
     *
     * @param encryptedBase64 The Base64-encoded encrypted message (IV + ciphertext).
     * @param key             The AES-256 secret key (must match the encryption key).
     * @return The original plaintext message.
     * @throws Exception if decryption fails or the message has been tampered with
     *                   (GCM authentication tag verification failure).
     */
    public static String decrypt(String encryptedBase64, SecretKey key) throws Exception {
        // Step 1: Decode from Base64
        byte[] combined = Base64.getDecoder().decode(encryptedBase64);

        // Step 2: Extract the IV (first 12 bytes)
        byte[] iv = new byte[IV_SIZE];
        System.arraycopy(combined, 0, iv, 0, IV_SIZE);

        // Step 3: Extract the ciphertext (everything after the IV)
        byte[] ciphertext = new byte[combined.length - IV_SIZE];
        System.arraycopy(combined, IV_SIZE, ciphertext, 0, ciphertext.length);

        // Step 4: Initialize cipher in DECRYPT mode
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);

        // Step 5: Decrypt (also verifies the GCM authentication tag)
        byte[] plaintext = cipher.doFinal(ciphertext);

        return new String(plaintext, "UTF-8");
    }

    /**
     * Checks whether a given string appears to be an encrypted message.
     * Encrypted messages are Base64-encoded and have a minimum length
     * (IV + at least 1 byte of ciphertext + auth tag).
     *
     * @param message The message to check.
     * @return true if the message looks like it could be encrypted.
     */
    public static boolean isEncrypted(String message) {
        if (message == null || message.isEmpty()) return false;
        try {
            byte[] decoded = Base64.getDecoder().decode(message);
            // Minimum size: 12 (IV) + 1 (data) + 16 (auth tag) = 29 bytes
            return decoded.length >= 29;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
