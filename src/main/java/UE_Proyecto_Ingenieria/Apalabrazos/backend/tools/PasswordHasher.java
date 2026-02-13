package UE_Proyecto_Ingenieria.Apalabrazos.backend.tools;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * Utility class for password hashing using PBKDF2 with HMAC-SHA256.
 */
public class PasswordHasher {

    private static final int ITERATIONS = 10000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 32;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    /**
     * Generates a random salt.
     *
     * @return Base64-encoded salt string
     */
    public static String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /**
     * Hashes a password with the given salt.
     *
     * @param password Plain text password
     * @param salt Base64-encoded salt
     * @return Base64-encoded hashed password
     * @throws RuntimeException if hashing fails
     */
    public static String hashPassword(String password, String salt) {
        try {
            byte[] saltBytes = Base64.getDecoder().decode(salt);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), saltBytes, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }

    /**
     * Verifies a password against a stored hash and salt.
     *
     * @param password Plain text password to verify
     * @param storedHash Stored Base64-encoded hash
     * @param salt Base64-encoded salt
     * @return true if password matches, false otherwise
     */
    public static boolean verifyPassword(String password, String storedHash, String salt) {
        String hashToVerify = hashPassword(password, salt);
        return hashToVerify.equals(storedHash);
    }
}
