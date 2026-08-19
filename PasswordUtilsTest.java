import org.junit.Test;
import static org.junit.Assert.*;

public class PasswordUtilsTest {

    @Test
    public void testHashAndVerify() {
        String password = "MySuperSecretPassword123!";
        
        String hash = PasswordUtils.hashPassword(password);
        assertNotNull("Hash should not be null", hash);
        assertNotEquals("Hash should not equal password", password, hash);
        
        // Verify with correct password
        assertTrue("Password verification should succeed", PasswordUtils.verifyPassword(password, hash));
        
        // Verify with wrong password
        assertFalse("Password verification should fail for wrong password", PasswordUtils.verifyPassword("wrongpassword", hash));
    }

    @Test
    public void testDifferentSaltsProduceDifferentHashes() {
        String password = "CommonPassword";
        
        String hash1 = PasswordUtils.hashPassword(password);
        String hash2 = PasswordUtils.hashPassword(password);
        
        assertNotEquals("Same password should produce different hashes due to salting", hash1, hash2);
        
        // Both hashes should still verify correctly against the password
        assertTrue(PasswordUtils.verifyPassword(password, hash1));
        assertTrue(PasswordUtils.verifyPassword(password, hash2));
    }
}
