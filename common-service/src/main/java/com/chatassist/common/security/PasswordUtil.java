package com.chatassist.common.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Password utility for hashing and verification using BCrypt.
 * BCrypt automatically handles salt and provides strong security.
 */
public class PasswordUtil {
    
    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    
    /**
     * Hash a plaintext password using BCrypt.
     *
     * @param rawPassword the plaintext password
     * @return BCrypt-hashed password
     */
    public static String hashPassword(String rawPassword) {
        return encoder.encode(rawPassword);
    }
    
    /**
     * Verify a plaintext password against a BCrypt-hashed password.
     *
     * @param rawPassword     the plaintext password to verify
     * @param hashedPassword  the BCrypt-hashed password from database
     * @return true if they match, false otherwise
     */
    public static boolean verifyPassword(String rawPassword, String hashedPassword) {
        return encoder.matches(rawPassword, hashedPassword);
    }
}

