package com.chatassist.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    @AfterEach
    void cleanupProperties() {
        System.clearProperty("chatassist.jwt.secret");
        System.clearProperty("chatassist.jwt.expiration-ms");
    }

    @Test
    @DisplayName("generateToken and extractPrincipal should round-trip user identity")
    void shouldGenerateAndParseToken() {
        System.setProperty("chatassist.jwt.secret", "unit-test-secret-key-minimum-32-char");

        String token = JwtUtil.generateToken(42L, "alex");

        assertThat(token).isNotBlank();
        assertThat(JwtUtil.isTokenValid(token)).isTrue();
        assertThat(JwtUtil.extractUsername(token)).isEqualTo("alex");
        assertThat(JwtUtil.extractUserId(token)).isEqualTo(42L);
        assertThat(JwtUtil.extractPrincipal(token)).isPresent();
    }

    @Test
    @DisplayName("extractTokenFromAuthorizationHeader should parse Bearer token")
    void shouldParseBearerHeader() {
        assertThat(JwtUtil.extractTokenFromAuthorizationHeader("Bearer abc.def.ghi")).isEqualTo("abc.def.ghi");
        assertThat(JwtUtil.extractTokenFromAuthorizationHeader("Basic xyz")).isNull();
        assertThat(JwtUtil.extractTokenFromAuthorizationHeader("Bearer   ")).isNull();
        assertThat(JwtUtil.extractTokenFromAuthorizationHeader(null)).isNull();
    }

    @Test
    @DisplayName("isTokenValid should return false for malformed token")
    void shouldRejectMalformedToken() {
        System.setProperty("chatassist.jwt.secret", "unit-test-secret-key-minimum-32-char");
        assertThat(JwtUtil.isTokenValid("this-is-not-a-jwt")).isFalse();
    }

    @Test
    @DisplayName("generateToken should fail for too-short secret")
    void shouldRejectShortSecret() {
        System.setProperty("chatassist.jwt.secret", "short-secret");

        assertThatThrownBy(() -> JwtUtil.generateToken(1L, "alex"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32");
    }

    @Test
    @DisplayName("token should expire based on configured expiration")
    void shouldExpireToken() throws InterruptedException {
        System.setProperty("chatassist.jwt.secret", "unit-test-secret-key-minimum-32-char");
        System.setProperty("chatassist.jwt.expiration-ms", "1");

        String token = JwtUtil.generateToken(7L, "expired-user");
        Thread.sleep(10L);

        assertThat(JwtUtil.isTokenValid(token)).isFalse();
        assertThat(JwtUtil.extractPrincipal(token)).isEmpty();
    }
}

