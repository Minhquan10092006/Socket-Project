import org.junit.Test;
import static org.junit.Assert.*;
import javax.crypto.SecretKey;

public class CryptoUtilsTest {

    @Test
    public void testKeyGeneration() throws Exception {
        SecretKey key = CryptoUtils.generateKey();
        assertNotNull("Generated key should not be null", key);
        assertEquals("Algorithm should be AES", "AES", key.getAlgorithm());
    }

    @Test
    public void testKeyToStringAndBack() throws Exception {
        SecretKey originalKey = CryptoUtils.generateKey();
        String keyString = CryptoUtils.keyToString(originalKey);
        assertNotNull("Key string should not be null", keyString);
        
        SecretKey reconstructedKey = CryptoUtils.stringToKey(keyString);
        assertNotNull("Reconstructed key should not be null", reconstructedKey);
        
        // Assert that the byte contents are identical
        assertArrayEquals("Keys should match after conversion", originalKey.getEncoded(), reconstructedKey.getEncoded());
    }

    @Test
    public void testEncryptAndDecrypt() throws Exception {
        SecretKey key = CryptoUtils.generateKey();
        String originalMessage = "Hello, SecureChat! This is a test message. @123";
        
        String encrypted = CryptoUtils.encrypt(originalMessage, key);
        assertNotNull("Encrypted string should not be null", encrypted);
        assertNotEquals("Encrypted string should not equal original", originalMessage, encrypted);
        
        String decrypted = CryptoUtils.decrypt(encrypted, key);
        assertEquals("Decrypted string should match original", originalMessage, decrypted);
    }
    
    @Test
    public void testDecryptWithWrongKeyShouldFail() throws Exception {
        SecretKey key1 = CryptoUtils.generateKey();
        SecretKey key2 = CryptoUtils.generateKey();
        
        String message = "Secret content";
        String encrypted = CryptoUtils.encrypt(message, key1);
        
        try {
            CryptoUtils.decrypt(encrypted, key2);
            fail("Expected exception when decrypting with wrong key");
        } catch (Exception e) {
            // Expected
        }
    }
}
