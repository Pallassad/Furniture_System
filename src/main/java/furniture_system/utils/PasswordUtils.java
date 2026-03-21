package furniture_system.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * PasswordUtils – SHA-256 hashing matching SQL Server HASHBYTES('SHA2_256', ...).
 * SQL Server stores the hash as a lowercase hex string (64 chars).
 */
public final class PasswordUtils {

    private PasswordUtils() {}

    /**
     * Hashes a plain-text password with SHA-256 and returns the lowercase hex string.
     * This matches: LOWER(CONVERT(NVARCHAR(64), HASHBYTES('SHA2_256', password), 2))
     */
    public static String hash(String plainText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(plainText.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Verifies a plain-text password against a stored hash.
     */
    public static boolean verify(String plainText, String storedHash) {
        return hash(plainText).equalsIgnoreCase(storedHash);
    }
}
