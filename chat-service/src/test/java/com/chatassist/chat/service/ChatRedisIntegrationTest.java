package com.chatassist.chat.service;

import com.chatassist.chat.entity.ChatMessage;
import com.chatassist.chat.repository.ChatMessageRepository;
import com.chatassist.common.dto.ChatMessageResponse;
import com.chatassist.common.model.MessageStatus;
import com.chatassist.common.model.MessageType;
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

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Redis caching behaviour in {@link ChatMessagingService}.
 *
 * <p>Containers:
 * <ul>
 *   <li>MySQL 8.0 – persists chat messages</li>
 *   <li>Redis 7   – backs the Spring Cache</li>
 * </ul>
 *
 * Kafka and WebSocket are mocked so only the Redis interaction is exercised.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("ChatService – Redis Cache Integration Tests")
class ChatRedisIntegrationTest {

    // ── Containers ────────────────────────────────────────────────────────────

    @Container
    static final MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("chat_service_db")
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
        // Prevent Kafka from trying to reach a real broker in this Redis-only test
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:29099");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
    }


    // ── Beans ─────────────────────────────────────────────────────────────────

    @Autowired
    private ChatMessageRepository messageRepository;

    @Autowired
    private ChatMessagingService chatMessagingService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // ── Setup ─────────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        var conn = stringRedisTemplate.getConnectionFactory().getConnection();
        conn.serverCommands().flushAll();
        conn.close();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private ChatMessage savedMessage(String sender, String receiver, String content) {
        Instant now = Instant.now();
        ChatMessage msg = new ChatMessage(1L, sender, 2L, receiver, content,
                MessageType.USER, MessageStatus.DELIVERED, now, now);
        return messageRepository.save(msg);
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Containers should be running before any test")
    void containersShouldBeRunning() {
        assertThat(mysqlContainer.isRunning()).isTrue();
        assertThat(redisContainer.isRunning()).isTrue();
    }

    @Test
    @DisplayName("getConversation() should write conversation to chat:conversations cache")
    void getConversationShouldPopulateRedisCache() {
        // Arrange
        savedMessage("alice", "bob", "Hello Bob!");
        savedMessage("bob", "alice", "Hey Alice!");

        // Act
        List<ChatMessageResponse> result = chatMessagingService.getConversation("alice", "bob");

        // Assert – messages returned
        assertThat(result).hasSize(2);

        // Assert – Redis cache entry exists (key is canonical: lexicographically smaller first)
        Set<String> keys = stringRedisTemplate.keys("chat-svc:v3::chat:conversations::*");
        assertThat(keys).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("getConversation() second call should be served from Redis (DB bypassed)")
    void getConversationSecondCallShouldUseCache() {
        // Arrange
        savedMessage("carol", "dave", "Hi Dave");

        // First call – populates cache
        List<ChatMessageResponse> firstCall = chatMessagingService.getConversation("carol", "dave");
        assertThat(firstCall).hasSize(1);

        // Add another message directly to DB (bypassing cache eviction)
        savedMessage("dave", "carol", "Hi Carol");

        // Second call – should return cached result (1 message, not 2)
        List<ChatMessageResponse> secondCall = chatMessagingService.getConversation("carol", "dave");
        assertThat(secondCall).hasSize(1);
    }

    @Test
    @DisplayName("getConversation() key should be canonical (order-independent)")
    void getConversationCacheKeyShouldBeCanonical() {
        // Arrange
        savedMessage("eve", "frank", "Hey");

        // Act – call with both orderings
        chatMessagingService.getConversation("eve", "frank");
        chatMessagingService.getConversation("frank", "eve");

        // Assert – only ONE cache entry because both orderings map to the same key
        Set<String> keys = stringRedisTemplate.keys("chat-svc:v3::chat:conversations::*");
        assertThat(keys).hasSize(1);
    }

    @Test
    @DisplayName("getDailyChatPeerSummary() should populate chat:activity:today cache")
    void getDailyChatPeerSummaryShouldPopulateActivityCache() {
        // Act
        chatMessagingService.getDailyChatPeerSummary("alice");

        // Assert
        Set<String> keys = stringRedisTemplate.keys("chat-svc:v3::chat:activity:today::*");
        assertThat(keys).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("getDailyChatPeerSummary() second call should be served from Redis")
    void getDailyChatPeerSummarySecondCallShouldUseCache() {
        // Prime the cache with alice having 1 peer
        savedMessage("alice", "bob", "Hello");
        var first = chatMessagingService.getDailyChatPeerSummary("alice");
        assertThat(first.chatPeerCount()).isEqualTo(1L);

        // Add more messages directly to DB (bypass cache)
        savedMessage("alice", "charlie", "Hi Charlie");

        // Second call – cache still reflects 1 peer
        var cached = chatMessagingService.getDailyChatPeerSummary("alice");
        assertThat(cached.chatPeerCount()).isEqualTo(1L);
    }
}
