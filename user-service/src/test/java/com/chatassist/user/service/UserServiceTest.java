package com.chatassist.user.service;

import com.chatassist.common.dto.AuthResponse;
import com.chatassist.common.dto.AssistantSummary;
import com.chatassist.common.dto.LoginRequest;
import com.chatassist.common.dto.RegisterUserRequest;
import com.chatassist.common.dto.UserActivitySummary;
import com.chatassist.common.dto.UserSummary;
import com.chatassist.common.security.PasswordUtil;
import com.chatassist.user.entity.ActivityEventType;
import com.chatassist.user.entity.AppUser;
import com.chatassist.user.entity.AuthAuditLog;
import com.chatassist.user.entity.UserActivityLog;
import com.chatassist.user.repository.AppUserRepository;
import com.chatassist.user.repository.AuthAuditLogRepository;
import com.chatassist.user.repository.UserActivityLogRepository;
import com.chatassist.user.repository.UserCredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Tests")
class UserServiceTest {

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private UserActivityLogRepository activityLogRepository;

    @Mock
    private UserCredentialRepository userCredentialRepository;

    @Mock
    private AuthAuditLogRepository authAuditLogRepository;

    private UserMapper userMapper;
    private UserService userService;

    private AppUser testUser;

    @BeforeEach
    void setUp() {
        userMapper = new UserMapper();
        userService = new UserService(userRepository, userMapper, activityLogRepository, userCredentialRepository, authAuditLogRepository);
        testUser = new AppUser("John", "Doe", "johndoe", "john@example.com", false);
    }

    @Test
    @DisplayName("Should register new user successfully")
    void testRegisterSuccess() {
        // Arrange
        RegisterUserRequest request = new RegisterUserRequest(
                "Jane", "Smith", "janesmith", "password123", "jane@example.com"
        );
        AppUser newUser = new AppUser(
                request.firstName(), request.lastName(), request.username(),
                request.email(), false
        );

        when(userRepository.existsByUsername(request.username())).thenReturn(false);
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.save(any(AppUser.class))).thenReturn(newUser);

        // Act
        AuthResponse response = userService.register(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.username()).isEqualTo("janesmith");
        assertThat(response.token()).isNull();
        assertThat(response.message()).isEqualTo("Registration successful. Please sign in.");
        verify(userRepository).existsByUsername(request.username());
        verify(userRepository).existsByEmail(request.email());
        verify(userRepository).save(any(AppUser.class));
        verify(userCredentialRepository).save(any());
        verify(activityLogRepository, never()).save(any(UserActivityLog.class));
    }

    @Test
    @DisplayName("Should throw CONFLICT when username already exists")
    void testRegisterDuplicateUsername() {
        // Arrange
        RegisterUserRequest request = new RegisterUserRequest(
                "Jane", "Smith", "janesmith", "password123", "jane@example.com"
        );
        when(userRepository.existsByUsername(request.username())).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Username already exists");
        
        verify(userRepository).existsByUsername(request.username());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw CONFLICT when email already exists")
    void testRegisterDuplicateEmail() {
        // Arrange
        RegisterUserRequest request = new RegisterUserRequest(
                "Jane", "Smith", "janesmith", "password123", "jane@example.com"
        );
        when(userRepository.existsByUsername(request.username())).thenReturn(false);
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Email already exists");
        
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should login user successfully")
    void testLoginSuccess() {
        // Arrange
        LoginRequest request = new LoginRequest("johndoe", "password123");
        when(userRepository.findByUsername(request.username())).thenReturn(Optional.of(testUser));
        when(userCredentialRepository.findById(testUser.getId())).thenReturn(Optional.of(new com.chatassist.user.entity.UserCredential(testUser.getId(), PasswordUtil.hashPassword("password123"))));

        // Act
        AuthResponse response = userService.login(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.username()).isEqualTo("johndoe");
        // Login must return a non-null JWT token for Bearer-auth clients
        assertThat(response.token()).isNotNull().isNotBlank();
        verify(userRepository).findByUsername(request.username());
        verify(activityLogRepository).save(any(UserActivityLog.class));
        verify(authAuditLogRepository).save(any(AuthAuditLog.class));
    }

    @Test
    @DisplayName("Should throw UNAUTHORIZED when user not found")
    void testLoginUserNotFound() {
        // Arrange
        LoginRequest request = new LoginRequest("nonexistent", "password123");
        when(userRepository.findByUsername(request.username())).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid username or password");
    }

    @Test
    @DisplayName("Should throw UNAUTHORIZED when password is incorrect")
    void testLoginIncorrectPassword() {
        // Arrange
        LoginRequest request = new LoginRequest("johndoe", "wrongpassword");
        when(userRepository.findByUsername(request.username())).thenReturn(Optional.of(testUser));
        when(userCredentialRepository.findById(testUser.getId())).thenReturn(Optional.of(new com.chatassist.user.entity.UserCredential(testUser.getId(), PasswordUtil.hashPassword("password123"))));

        // Act & Assert
        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid username or password");
    }

    @Test
    @DisplayName("Should list users excluding specified username")
    void testListUsersExcluding() {
        // Arrange
        String excludeUsername = "admin";
        AppUser user1 = new AppUser("User", "One", "user1", "user1@test.com", false);
        AppUser user2 = new AppUser("User", "Two", "user2", "user2@test.com", false);
        List<AppUser> users = List.of(user1, user2);

        when(userRepository.findByUsernameNotOrderByBotAscFirstNameAsc(excludeUsername))
                .thenReturn(users);

        // Act
        List<UserSummary> result = userService.listUsers(excludeUsername);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).username()).isEqualTo("user1");
        assertThat(result.get(1).username()).isEqualTo("user2");
        verify(userRepository).findByUsernameNotOrderByBotAscFirstNameAsc(excludeUsername);
    }

    @Test
    @DisplayName("Should list assistants")
    void testListAssistants() {
        // Act
        List<AssistantSummary> assistants = userService.listAssistants();

        // Assert
        assertThat(assistants).isNotEmpty();
    }

    @Test
    @DisplayName("Should mark user online")
    void testMarkUserOnline() {
        // Arrange
        String username = "johndoe";

        // Act
        userService.markUserOnline(username);

        // Assert
        verify(userRepository).updateOnlineStatus(username, true);
    }

    @Test
    @DisplayName("Should mark user offline")
    void testMarkUserOffline() {
        // Arrange
        String username = "johndoe";

        // Act
        userService.markUserOffline(username);

        // Assert
        verify(userRepository).updateOnlineStatus(username, false);
    }

    @Test
    @DisplayName("Should logout user and record logout event")
    void testLogout() {
        // Arrange
        String username = "johndoe";

        // Act
        userService.logout(username);

        // Assert
        verify(userRepository).updateOnlineStatus(username, false);
        ArgumentCaptor<UserActivityLog> captor = ArgumentCaptor.forClass(UserActivityLog.class);
        verify(activityLogRepository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo(username);
    }

    @Test
    @DisplayName("Should get activity summary for user")
    void testGetActivitySummary() {
        // Arrange
        String username = "johndoe";
        LocalDate today = LocalDate.now();
        when(activityLogRepository.countByUsernameAndDateAndType(username, today, ActivityEventType.LOGIN))
                .thenReturn(2L);
        when(activityLogRepository.countByUsernameAndDateAndType(username, today, ActivityEventType.LOGOUT))
                .thenReturn(2L);

        // Act
        UserActivitySummary summary = userService.getActivitySummary(username);

        // Assert
        assertThat(summary).isNotNull();
        assertThat(summary.username()).isEqualTo(username);
        assertThat(summary.date()).isEqualTo(today);
        assertThat(summary.loginCount()).isEqualTo(2L);
        assertThat(summary.logoutCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Should get activity summaries for all users")
    void testGetAllUsersActivitySummary() {
        // Arrange
        LocalDate today = LocalDate.now();
        Object[] row1 = {"user1", 3L, 1L};
        Object[] row2 = {"user2", 2L, 2L};
        List<Object[]> mockResults = List.of(row1, row2);

        when(activityLogRepository.findActivityCountsForDate(today))
                .thenReturn(mockResults);

        // Act
        List<UserActivitySummary> summaries = userService.getAllUsersActivitySummary();

        // Assert
        assertThat(summaries).hasSize(2);
        assertThat(summaries.get(0).username()).isEqualTo("user1");
        assertThat(summaries.get(0).loginCount()).isEqualTo(3L);
        assertThat(summaries.get(1).username()).isEqualTo("user2");
        assertThat(summaries.get(1).loginCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Should find user by username")
    void testFindByUsername() {
        // Arrange
        when(userRepository.findByUsername("johndoe")).thenReturn(Optional.of(testUser));

        // Act
        UserSummary result = userService.findByUsername("johndoe");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.username()).isEqualTo("johndoe");
        verify(userRepository).findByUsername("johndoe");
    }

    @Test
    @DisplayName("Should throw NOT_FOUND when user does not exist")
    void testFindByUsernameNotFound() {
        // Arrange
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.findByUsername("nonexistent"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("User not found");
    }

}

