package com.chatassist.user.service;

import com.chatassist.common.dto.UserSummary;
import com.chatassist.user.entity.AppUser;
import com.chatassist.user.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Redis caching behaviour in {@link UserService}.
 *
 * <p>Two Testcontainers are started:
 * <ul>
 *   <li>MySQL 8.0 – stores user data</li>
 *   <li>Redis 7 – backs the Spring Cache</li>
 * </ul>
 *
 * Each test verifies that the {@code @Cacheable} / {@code @CacheEvict} annotations
 * on {@link UserService} interact correctly with a real Redis instance.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("UserService – Redis Cache Integration Tests")
class UserRedisCacheIntegrationTest {

    // ── Containers ────────────────────────────────────────────────────────────

    @Container
    static final MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("user_service_db")
            .withUsername("test_user")
            .withPassword("test_password")
            .withCommand("--default-authentication-plugin=mysql_native_password")
            .withEnv("MYSQL_INITDB_SKIP_TZINFO", "yes");

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> redisContainer =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    // ── Dynamic properties ────────────────────────────────────────────────────

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // MySQL
        registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.password", mysqlContainer::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        // Redis
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port",
                () -> redisContainer.getMappedPort(6379).toString());
        registry.add("spring.cache.type", () -> "redis");
    }

    // ── Beans ─────────────────────────────────────────────────────────────────

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // ── Setup ─────────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        // Flush all Redis keys so tests are isolated
        stringRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Containers should be running before any test")
    void containersShouldBeRunning() {
        assertThat(mysqlContainer.isRunning()).isTrue();
        assertThat(redisContainer.isRunning()).isTrue();
    }

    @Test
    @DisplayName("listUsers() should write an entry to the Redis users:list cache")
    void listUsersShouldPopulateUsersListCache() {
        // Arrange
        userRepository.save(new AppUser("Alice", "Smith", "alice", "alice@test.com", false));
        userRepository.save(new AppUser("Bob", "Jones", "bob", "bob@test.com", false));

        // Act – first invocation should hit the DB and populate the cache
        List<UserSummary> result = userService.listUsers("nobody");

        // Assert – result size is correct
        assertThat(result).hasSize(2);

        // Assert – Redis contains at least one key for the "users:list" cache
        Set<String> keys = stringRedisTemplate.keys("user-svc:v3::users:list::*");
        assertThat(keys).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("listUsers() second call should be served from Redis (DB bypassed)")
    void listUsersShouldServeSecondCallFromCache() {
        // Arrange
        userRepository.save(new AppUser("Charlie", "Brown", "charlie", "c@test.com", false));

        // First call – populates cache
        List<UserSummary> firstCall = userService.listUsers("nobody");
        assertThat(firstCall).hasSize(1);
        
        // Verify cache was populated
        Set<String> cacheKeysBefore = stringRedisTemplate.keys("user-svc:v3::users:list::*");
        assertThat(cacheKeysBefore).isNotNull().isNotEmpty();

        // Mutate DB directly (bypassing cache) – add a new user
        userRepository.save(new AppUser("Delta", "Echo", "delta", "d@test.com", false));

        // Second call with the same key – must return cached (stale) view: 1 user
        List<UserSummary> secondCall = userService.listUsers("nobody");
        assertThat(secondCall).hasSize(1); // cache still holds the original 1-user list
    }

    @Test
    @DisplayName("findByUsername() should write an entry to the Redis users:profile cache")
    void findByUsernameShouldPopulateProfileCache() {
        // Arrange
        userRepository.save(new AppUser("Eve", "Fox", "evefox", "eve@test.com", false));

        // Act
        UserSummary profile = userService.findByUsername("evefox");

        // Assert
        assertThat(profile).isNotNull();
        assertThat(profile.username()).isEqualTo("evefox");

        Set<String> keys = stringRedisTemplate.keys("user-svc:v3::users:profile::*");
        assertThat(keys).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("findByUsername() second call should be served from Redis (DB bypassed)")
    void findByUsernameShouldServeSecondCallFromCache() {
        // Arrange
        userRepository.save(new AppUser("Frank", "Gray", "frank", "f@test.com", false));
        
        // First call: should succeed and populate cache
        UserSummary firstCall = userService.findByUsername("frank");
        assertThat(firstCall).isNotNull();
        assertThat(firstCall.username()).isEqualTo("frank");

        // Delete the record from DB directly
        userRepository.deleteAll();

        // Second call: should still return the cached profile, not throw NOT_FOUND
        UserSummary cachedProfile = userService.findByUsername("frank");
        assertThat(cachedProfile).isNotNull();
        assertThat(cachedProfile.username()).isEqualTo("frank");
    }

    @Test
    @DisplayName("markUserOnline() should evict the users:list cache")
    void markUserOnlineShouldEvictUsersListCache() {
        // Arrange
        userRepository.save(new AppUser("Grace", "Hill", "grace", "g@test.com", false));

        // Populate cache
        userService.listUsers("nobody");
        Set<String> keysBefore = stringRedisTemplate.keys("user-svc:v3::users:list::*");
        assertThat(keysBefore).isNotNull().isNotEmpty();

        // Act – triggers @CacheEvict(USERS_LIST, allEntries=true)
        userService.markUserOnline("grace");

        // Assert – cache entries are gone
        Set<String> keysAfter = stringRedisTemplate.keys("user-svc:v3::users:list::*");
        assertThat(keysAfter).isNullOrEmpty();
    }

    @Test
    @DisplayName("markUserOffline() should evict users:list AND users:profile caches")
    void markUserOfflineShouldEvictBothCaches() {
        // Arrange
        userRepository.save(new AppUser("Henry", "Ice", "henry", "h@test.com", false));

        // Populate both caches
        userService.listUsers("nobody");
        userService.findByUsername("henry");

        // Act
        userService.markUserOffline("henry");

        // Assert – both caches evicted
        assertThat(stringRedisTemplate.keys("user-svc:v3::users:list::*")).isNullOrEmpty();
        assertThat(stringRedisTemplate.keys("user-svc:v3::users:profile::henry")).isNullOrEmpty();
    }

    @Test
    @DisplayName("listAssistants() should write an entry to the Redis users:assistants cache")
    void listAssistantsShouldPopulateAssistantsCache() {
        // Act
        userService.listAssistants();

        // Assert
        Set<String> keys = stringRedisTemplate.keys("user-svc:v3::users:assistants::*");
        assertThat(keys).isNotNull().isNotEmpty();
    }
}

