package com.chatassist.user.service;

import com.chatassist.user.entity.AppUser;
import com.chatassist.user.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("UserService Integration Tests with MySQL TestContainer")
class UserServiceIntegrationTest {

    @Container
    static final MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("user_service_db")
            .withUsername("test_user")
            .withPassword("test_password")
            .withCommand("--default-authentication-plugin=mysql_native_password")
            .withEnv("MYSQL_INITDB_SKIP_TZINFO", "yes");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.password", mysqlContainer::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.cache.type", () -> "none");
    }

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private UserService userService;

    private AppUser testUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        testUser = new AppUser("John", "Doe", "johndoe", "password123", "john@example.com", false);
    }

    @Test
    @DisplayName("Should persist user to database and retrieve it")
    void testUserPersistence() {
        // Arrange & Act
        AppUser savedUser = userRepository.save(testUser);

        // Assert
        assertThat(savedUser.getId()).isNotNull();
        assertThat(savedUser.getUsername()).isEqualTo("johndoe");
        assertThat(savedUser.getEmail()).isEqualTo("john@example.com");

        // Verify retrieval
        AppUser retrievedUser = userRepository.findByUsername("johndoe").orElse(null);
        assertThat(retrievedUser).isNotNull();
        assertThat(retrievedUser.getFirstName()).isEqualTo("John");
    }

    @Test
    @DisplayName("Should verify database connection is working")
    void testDatabaseConnection() {
        // Act
        long userCount = userRepository.count();

        // Assert
        assertThat(userCount).isZero();

        // Add users and verify count
        userRepository.save(testUser);
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should handle multiple user registrations")
    void testMultipleUserRegistration() {
        // Arrange
        AppUser user1 = new AppUser("Alice", "Smith", "alice", "pass1", "alice@test.com", false);
        AppUser user2 = new AppUser("Bob", "Jones", "bob", "pass2", "bob@test.com", false);
        AppUser user3 = new AppUser("Charlie", "Brown", "charlie", "pass3", "charlie@test.com", false);

        // Act
        userRepository.save(user1);
        userRepository.save(user2);
        userRepository.save(user3);

        // Assert
        assertThat(userRepository.count()).isEqualTo(3);
        assertThat(userRepository.findByUsername("alice")).isPresent();
        assertThat(userRepository.findByUsername("bob")).isPresent();
        assertThat(userRepository.findByUsername("charlie")).isPresent();
    }

    @Test
    @DisplayName("Should verify unique constraint on username")
    void testUsernameUniqueness() {
        // Arrange
        userRepository.save(testUser);

        // Act & Assert - Try to save duplicate
        AppUser duplicateUser = new AppUser("Jane", "Doe", "johndoe", "different", "jane@example.com", false);
        try {
            userRepository.save(duplicateUser);
            userRepository.flush();
            // If we reach here, test fails - no exception thrown
            throw new AssertionError("Expected exception for duplicate username but none was thrown");
        } catch (Exception e) {
            // Expected - constraint violation thrown
            assertThat(e).isNotNull();
        }
    }

    @Test
    @DisplayName("Should verify unique constraint on email")
    void testEmailUniqueness() {
        // Arrange
        userRepository.save(testUser);

        // Act & Assert - Try to save duplicate email
        AppUser duplicateEmail = new AppUser("Jane", "Doe", "janedoe", "password", "john@example.com", false);
        try {
            userRepository.save(duplicateEmail);
            userRepository.flush();
            // If we reach here, test fails - no exception thrown
            throw new AssertionError("Expected exception for duplicate email but none was thrown");
        } catch (Exception e) {
            // Expected - constraint violation thrown
            assertThat(e).isNotNull();
        }
    }

    @Test
    @DisplayName("Should test online status persistence")
    void testOnlineStatusPersistence() {
        // Arrange
        AppUser savedUser = userRepository.save(testUser);
        Long userId = savedUser.getId();

        // Act
        savedUser.setOnline(true);
        userRepository.save(savedUser);

        // Assert
        AppUser retrievedUser = userRepository.findById(userId).orElse(null);
        assertThat(retrievedUser).isNotNull();
        assertThat(retrievedUser.isOnline()).isTrue();
    }

    @Test
    @DisplayName("Should perform bulk user queries efficiently")
    void testBulkUserQueries() {
        // Arrange
        for (int i = 0; i < 100; i++) {
            AppUser user = new AppUser(
                    "First" + i, "Last" + i, "user" + i,
                    "pass" + i, "user" + i + "@test.com", false
            );
            userRepository.save(user);
        }

        // Act
        long totalUsers = userRepository.count();

        // Assert
        assertThat(totalUsers).isEqualTo(100);
    }
}

