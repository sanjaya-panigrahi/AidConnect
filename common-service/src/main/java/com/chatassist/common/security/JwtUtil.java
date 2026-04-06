package com.chatassist.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Optional;

/**
 * JWT utility for token generation and validation.
 * Uses HMAC-SHA256 with a strong secret key.
 */
public class JwtUtil {
    public static final String USER_ID_CLAIM = "userId";

    private static final String DEFAULT_SECRET = "chat-assist-jwt-secret-key-min-32-chars-required-by-hs256";
    private static final long DEFAULT_EXPIRATION_MS = 24 * 60 * 60 * 1000;

    private static final String SECRET_PROPERTY = "chatassist.jwt.secret";
    private static final String SECRET_ENV = "JWT_SECRET";

    private static final String EXPIRATION_PROPERTY = "chatassist.jwt.expiration-ms";
    private static final String EXPIRATION_ENV = "JWT_EXPIRATION_MS";

    private JwtUtil() {
        // Utility class
    }

    private static SecretKey signingKey() {
        String secret = resolveSecret();
        if (secret.length() < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 characters long");
        }
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    private static String resolveSecret() {
        String propertyValue = System.getProperty(SECRET_PROPERTY);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }
        String envValue = System.getenv(SECRET_ENV);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return DEFAULT_SECRET;
    }

    private static long resolveExpirationMs() {
        String value = System.getProperty(EXPIRATION_PROPERTY);
        if (value == null || value.isBlank()) {
            value = System.getenv(EXPIRATION_ENV);
        }
        if (value == null || value.isBlank()) {
            return DEFAULT_EXPIRATION_MS;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed > 0 ? parsed : DEFAULT_EXPIRATION_MS;
        } catch (NumberFormatException ex) {
            return DEFAULT_EXPIRATION_MS;
        }
    }
    
    /**
     * Generate a JWT token for a user.
     *
     * @param userId     the user ID
     * @param username   the username
     * @return JWT token string
     */
    public static String generateToken(Long userId, String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + resolveExpirationMs());
        return Jwts.builder()
                .subject(username)
                .claim(USER_ID_CLAIM, userId)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey())
                .compact();
    }
    
    /**
     * Validate and extract claims from a JWT token.
     *
     * @param token the JWT token string
     * @return claims if valid, null if invalid
     */
    public static Claims validateAndExtractClaims(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
    
    /**
     * Extract username from a JWT token.
     *
     * @param token the JWT token string
     * @return username, or null if token is invalid
     */
    public static String extractUsername(String token) {
        Claims claims = validateAndExtractClaims(token);
        return claims != null ? claims.getSubject() : null;
    }
    
    /**
     * Extract userId from a JWT token.
     *
     * @param token the JWT token string
     * @return userId, or null if token is invalid
     */
    public static Long extractUserId(String token) {
        Claims claims = validateAndExtractClaims(token);
        if (claims != null) {
            Object userIdObj = claims.get(USER_ID_CLAIM);
            if (userIdObj instanceof Number) {
                return ((Number) userIdObj).longValue();
            }
        }
        return null;
    }

    /**
     * Extracts the JWT token from a standard Authorization header.
     */
    public static String extractTokenFromAuthorizationHeader(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }
        if (!authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authorizationHeader.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    /**
     * Convenience parser that returns user identity if token is valid.
     */
    public static Optional<JwtPrincipal> extractPrincipal(String token) {
        String username = extractUsername(token);
        Long userId = extractUserId(token);
        if (username == null || userId == null) {
            return Optional.empty();
        }
        return Optional.of(new JwtPrincipal(userId, username));
    }

    public record JwtPrincipal(Long userId, String username) {
    }
    
    /**
     * Check if a JWT token is valid (not expired and properly signed).
     *
     * @param token the JWT token string
     * @return true if valid, false otherwise
     */
    public static boolean isTokenValid(String token) {
        return extractPrincipal(token).isPresent();
    }
}

