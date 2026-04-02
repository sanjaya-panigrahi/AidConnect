package com.chatassist.user.service;

import com.chatassist.common.dto.AuthResponse;
import com.chatassist.common.dto.UserSummary;
import com.chatassist.user.entity.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

@DisplayName("UserMapper Tests")
class UserMapperTest {

    private UserMapper userMapper;
    private AppUser testUser;

    @BeforeEach
    void setUp() {
        userMapper = new UserMapper();
        testUser = new AppUser("John", "Doe", "johndoe", "password123", "john@example.com", false);
    }

    @Test
    @DisplayName("Should map AppUser to AuthResponse correctly")
    void testToAuthResponse() {
        // Act
        AuthResponse response = userMapper.toAuthResponse(testUser, "Login successful");

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.username()).isEqualTo("johndoe");
        assertThat(response.firstName()).isEqualTo("John");
        assertThat(response.lastName()).isEqualTo("Doe");
        assertThat(response.email()).isEqualTo("john@example.com");
        assertThat(response.message()).isEqualTo("Login successful");
    }

    @Test
    @DisplayName("Should map AppUser to UserSummary correctly")
    void testToSummary() {
        // Arrange
        testUser.setOnline(true);

        // Act
        UserSummary summary = userMapper.toSummary(testUser);

        // Assert
        assertThat(summary).isNotNull();
        assertThat(summary.username()).isEqualTo("johndoe");
        assertThat(summary.firstName()).isEqualTo("John");
        assertThat(summary.lastName()).isEqualTo("Doe");
        assertThat(summary.email()).isEqualTo("john@example.com");
        assertThat(summary.bot()).isFalse();
        assertThat(summary.online()).isTrue();
    }

    @Test
    @DisplayName("Should preserve user ID in mappings")
    void testIdPreservation() {
        // Act - IDs will be null for new unsaved users
        AuthResponse authResponse = userMapper.toAuthResponse(testUser, "Test");
        UserSummary summary = userMapper.toSummary(testUser);

        // Assert - Just verify the fields are mapped consistently
        assertThat(authResponse.userId()).isEqualTo(testUser.getId());
        assertThat(summary.id()).isEqualTo(testUser.getId());
    }

    @Test
    @DisplayName("Should handle bot users in summary")
    void testBotUserSummary() {
        // Arrange
        AppUser botUser = new AppUser("Bot", "Assistant", "bot", "password", "bot@example.com", true);

        // Act
        UserSummary summary = userMapper.toSummary(botUser);

        // Assert
        assertThat(summary.bot()).isTrue();
    }

    @Test
    @DisplayName("Should handle different auth messages")
    void testDifferentAuthMessages() {
        // Act
        AuthResponse loginResponse = userMapper.toAuthResponse(testUser, "Login successful");
        AuthResponse registerResponse = userMapper.toAuthResponse(testUser, "Registration successful");

        // Assert
        assertThat(loginResponse.message()).isEqualTo("Login successful");
        assertThat(registerResponse.message()).isEqualTo("Registration successful");
    }

    @Test
    @DisplayName("Should map offline user status correctly")
    void testOfflineUserMapping() {
        // Arrange
        testUser.setOnline(false);

        // Act
        UserSummary summary = userMapper.toSummary(testUser);

        // Assert
        assertThat(summary.online()).isFalse();
    }
}

