package com.chatassist.common.security;

/**
 * Utility methods for server-side auth session keys/values stored in Redis.
 */
public final class AuthSessionKeyUtil {
    private static final String SESSION_PREFIX = "auth:session:";
    public static final String SESSION_USER_ID_ATTRIBUTE = "auth.userId";
    public static final String SESSION_USERNAME_ATTRIBUTE = "auth.username";
    public static final String SESSION_AUTH_CREATED_AT_ATTRIBUTE = "auth.createdAtEpochMs";

    private AuthSessionKeyUtil() {
    }

    public static String buildSessionKey(String token) {
        return SESSION_PREFIX + token;
    }

    public static String buildSessionValue(Long userId, String username) {
        return userId + ":" + username;
    }

    public static boolean matches(String storedValue, Long userId, String username) {
        return buildSessionValue(userId, username).equals(storedValue);
    }
}

