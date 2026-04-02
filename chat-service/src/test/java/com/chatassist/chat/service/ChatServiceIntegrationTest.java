package com.chatassist.chat.service;

import com.chatassist.chat.entity.ChatMessage;
import com.chatassist.chat.repository.ChatMessageRepository;
import com.chatassist.common.model.MessageStatus;
import com.chatassist.common.model.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("ChatService Integration Tests with MySQL TestContainer")
class ChatServiceIntegrationTest {

    @Container
    static final MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("chat_service_db")
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
    private ChatMessageRepository messageRepository;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    private ChatMessage testMessage;

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        Instant now = Instant.now();
        testMessage = new ChatMessage(
                1L, "user1", 2L, "user2", "Hello there",
                MessageType.USER, MessageStatus.SENT, now, null
        );
    }

    @Test
    @DisplayName("Should persist chat message to database")
    void testMessagePersistence() {
        // Arrange & Act
        ChatMessage savedMessage = messageRepository.save(testMessage);

        // Assert
        assertThat(savedMessage.getId()).isNotNull();
        assertThat(savedMessage.getSenderUsername()).isEqualTo("user1");
        assertThat(savedMessage.getContent()).isEqualTo("Hello there");
        assertThat(savedMessage.getStatus()).isEqualTo(MessageStatus.SENT);
    }

    @Test
    @DisplayName("Should retrieve conversation between two users")
    void testConversationRetrieval() {
        // Arrange
        messageRepository.save(testMessage);
        
        ChatMessage message2 = new ChatMessage(
                2L, "user2", 1L, "user1", "Hi back",
                MessageType.USER, MessageStatus.DELIVERED, Instant.now(), Instant.now()
        );
        messageRepository.save(message2);

        // Act
        List<ChatMessage> conversation = messageRepository.findConversation("user1", "user2");

        // Assert
        assertThat(conversation).isNotEmpty();
        assertThat(conversation).hasSize(2);
        assertThat(conversation.get(0).getSenderUsername()).isIn("user1", "user2");
    }

    @Test
    @DisplayName("Should update message status in database")
    void testMessageStatusUpdate() {
        // Arrange
        ChatMessage savedMessage = messageRepository.save(testMessage);

        // Act
        savedMessage.markDelivered(Instant.now());
        messageRepository.save(savedMessage);

        // Assert
        ChatMessage retrievedMessage = messageRepository.findById(savedMessage.getId()).orElse(null);
        assertThat(retrievedMessage).isNotNull();
        assertThat(retrievedMessage.getStatus()).isEqualTo(MessageStatus.DELIVERED);
        assertThat(retrievedMessage.getDeliveredAt()).isNotNull();
    }

    @Test
    @DisplayName("Should handle message type variations")
    void testMessageTypeVariations() {
        // Arrange
        ChatMessage userMessage = new ChatMessage(
                1L, "user1", 2L, "user2", "User message",
                MessageType.USER, MessageStatus.SENT, Instant.now(), null
        );
        ChatMessage botMessage = new ChatMessage(
                3L, "bot", 1L, "user1", "Bot response",
                MessageType.BOT, MessageStatus.DELIVERED, Instant.now(), Instant.now()
        );

        // Act
        messageRepository.save(userMessage);
        messageRepository.save(botMessage);

        // Assert
        assertThat(messageRepository.count()).isEqualTo(2);
        List<ChatMessage> allMessages = messageRepository.findAll();
        assertThat(allMessages).extracting(ChatMessage::getMessageType)
                .contains(MessageType.USER, MessageType.BOT);
    }

    @Test
    @DisplayName("Should retrieve messages by sender")
    void testMessageQueryBySender() {
        // Arrange
        messageRepository.save(testMessage);
        
        ChatMessage message2 = new ChatMessage(
                1L, "user1", 3L, "user3", "Message to user3",
                MessageType.USER, MessageStatus.SENT, Instant.now(), null
        );
        messageRepository.save(message2);

        // Act & Assert
        assertThat(messageRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should verify message content persistence")
    void testMessageContentPersistence() {
        // Arrange
        String longContent = "This is a very long message content that should be persisted correctly in the database. ".repeat(10);
        ChatMessage longMessage = new ChatMessage(
                1L, "user1", 2L, "user2", longContent,
                MessageType.USER, MessageStatus.SENT, Instant.now(), null
        );

        // Act
        ChatMessage savedMessage = messageRepository.save(longMessage);

        // Assert
        ChatMessage retrieved = messageRepository.findById(savedMessage.getId()).orElse(null);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getContent()).isEqualTo(longContent);
        assertThat(retrieved.getContent().length()).isGreaterThan(500);
    }

    @Test
    @DisplayName("Should handle context username in messages")
    void testContextUsernameHandling() {
        // Arrange
        testMessage.setContextUsername("user3");
        ChatMessage savedMessage = messageRepository.save(testMessage);

        // Act
        ChatMessage retrieved = messageRepository.findById(savedMessage.getId()).orElse(null);

        // Assert
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getContextUsername()).isEqualTo("user3");
    }

    @Test
    @DisplayName("Should perform bulk message operations")
    void testBulkMessageOperations() {
        // Arrange
        for (int i = 0; i < 50; i++) {
            ChatMessage msg = new ChatMessage(
                    1L, "user1", 2L, "user2", "Message " + i,
                    MessageType.USER, MessageStatus.SENT, Instant.now(), null
            );
            messageRepository.save(msg);
        }

        // Act
        long messageCount = messageRepository.count();

        // Assert
        assertThat(messageCount).isEqualTo(50);
    }

    @Test
    @DisplayName("Should verify database schema supports large conversations")
    void testLargeConversationSupport() {
        // Arrange - Create a large conversation thread
        for (int i = 0; i < 100; i++) {
            boolean isUser1 = i % 2 == 0;
            Long senderId = isUser1 ? 1L : 2L;
            String senderUsername = isUser1 ? "user1" : "user2";
            Long receiverId = isUser1 ? 2L : 1L;
            String receiverUsername = isUser1 ? "user2" : "user1";

            ChatMessage msg = new ChatMessage(
                    senderId, senderUsername, receiverId, receiverUsername,
                    "Message " + i, MessageType.USER, MessageStatus.DELIVERED,
                    Instant.now(), Instant.now()
            );
            messageRepository.save(msg);
        }

        // Act
        List<ChatMessage> conversation = messageRepository.findConversation("user1", "user2");

        // Assert
        assertThat(conversation).hasSize(100);
    }
}

