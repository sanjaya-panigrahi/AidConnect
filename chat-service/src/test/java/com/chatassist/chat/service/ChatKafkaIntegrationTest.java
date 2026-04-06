package com.chatassist.chat.service;

import com.chatassist.chat.repository.ChatMessageRepository;
import com.chatassist.common.dto.ChatMessageEvent;
import com.chatassist.common.model.MessageStatus;
import com.chatassist.common.model.MessageType;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying that {@link ChatEventPublisher} actually delivers
 * {@link ChatMessageEvent} records to the {@code chat-messages} Kafka topic.
 *
 * <p>Containers:
 * <ul>
 *   <li>MySQL 8.0 – persists chat messages</li>
 *   <li>Redis 7   – backs the Spring Cache</li>
 *   <li>Kafka     – Confluent Platform broker (KRaft mode)</li>
 * </ul>
 *
 * WebSocket is mocked so no broker is needed for STOMP.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("ChatService – Kafka Publisher Integration Tests")
class ChatKafkaIntegrationTest {

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

    @Container
    static final KafkaContainer kafkaContainer =
            // Keep Confluent image for KafkaContainer startup scripts; avoid withKraft() to prevent
            // image verification/pull during class initialization.
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

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
        // Kafka
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
    }

    // ── Mocks ─────────────────────────────────────────────────────────────────

    @MockitoBean
    SimpMessagingTemplate simpMessagingTemplate;

    // ── Beans ─────────────────────────────────────────────────────────────────

    @Autowired
    private ChatEventPublisher chatEventPublisher;

    @Autowired
    private ChatMessageRepository messageRepository;

    // ── Setup ─────────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Creates a plain String consumer pointing at the Kafka Testcontainer.
     * JSON payload is received as raw String so we can assert field values
     * without needing type-headers.
     */
    private Consumer<String, String> createTestConsumer(String groupId) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                kafkaContainer.getBootstrapServers(), groupId, "true");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        DefaultKafkaConsumerFactory<String, String> factory =
                new DefaultKafkaConsumerFactory<>(props);
        Consumer<String, String> consumer = factory.createConsumer();
        consumer.subscribe(Collections.singletonList("chat-messages"));
        return consumer;
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Containers should be running before any test")
    void containersShouldBeRunning() {
        assertThat(mysqlContainer.isRunning()).isTrue();
        assertThat(redisContainer.isRunning()).isTrue();
        assertThat(kafkaContainer.isRunning()).isTrue();
    }

    @Test
    @DisplayName("publish() should send a ChatMessageEvent to the chat-messages topic")
    void publishShouldSendEventToKafkaTopic() {
        // Arrange
        ChatMessageEvent event = new ChatMessageEvent(
                1L, 1L, "alice", 2L, "bob", null,
                "Hello from integration test",
                MessageType.USER, MessageStatus.DELIVERED,
                Instant.now(), Instant.now(), null, false
        );

        Consumer<String, String> consumer = createTestConsumer("test-publish-group");
        // Drain any existing records before publishing
        consumer.poll(Duration.ofMillis(500));

        // Act
        chatEventPublisher.publish(event);

        // Assert – poll with generous timeout to allow producer flush
        ConsumerRecords<String, String> records =
                KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(15));

        assertThat(records.count()).isGreaterThanOrEqualTo(1);

        ConsumerRecord<String, String> record = records.iterator().next();
        // Key is the receiverUsername (partition routing by receiver)
        assertThat(record.key()).isEqualTo("bob");
        // Value is JSON containing the message fields
        assertThat(record.value()).contains("alice");
        assertThat(record.value()).contains("Hello from integration test");

        consumer.close();
    }

    @Test
    @DisplayName("publish() should route event with the correct Kafka partition key (receiverUsername)")
    void publishShouldUseReceiverUsernameAsKey() {
        // Arrange
        ChatMessageEvent event1 = new ChatMessageEvent(
                10L, 1L, "user1", 2L, "charlie", null, "Msg to charlie",
                MessageType.USER, MessageStatus.DELIVERED,
                Instant.now(), Instant.now(), null, false
        );
        ChatMessageEvent event2 = new ChatMessageEvent(
                11L, 1L, "user1", 3L, "diana", null, "Msg to diana",
                MessageType.USER, MessageStatus.DELIVERED,
                Instant.now(), Instant.now(), null, false
        );

        Consumer<String, String> consumer = createTestConsumer("test-routing-group");
        consumer.poll(Duration.ofMillis(500));

        // Act
        chatEventPublisher.publish(event1);
        chatEventPublisher.publish(event2);

        // Assert – we expect at least 2 records with the respective keys
        ConsumerRecords<String, String> records =
                KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(15));

        assertThat(records.count()).isGreaterThanOrEqualTo(2);

        boolean charlieFound = false;
        boolean dianaFound = false;
        for (ConsumerRecord<String, String> r : records) {
            if ("charlie".equals(r.key())) charlieFound = true;
            if ("diana".equals(r.key())) dianaFound = true;
        }
        assertThat(charlieFound).isTrue();
        assertThat(dianaFound).isTrue();

        consumer.close();
    }

    @Test
    @DisplayName("publish() should serialise the generatedByBot flag correctly")
    void publishShouldSerialiseGeneratedByBotFlag() {
        // Arrange – bot-generated reply
        ChatMessageEvent botEvent = new ChatMessageEvent(
                99L, 3L, "bot", 1L, "alice", null, "I am a bot reply",
                MessageType.BOT, MessageStatus.DELIVERED,
                Instant.now(), Instant.now(), null, true  // generatedByBot = true
        );

        Consumer<String, String> consumer = createTestConsumer("test-bot-flag-group");
        consumer.poll(Duration.ofMillis(500));

        // Act
        chatEventPublisher.publish(botEvent);

        // Assert
        ConsumerRecords<String, String> records =
                KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(15));

        assertThat(records.count()).isGreaterThanOrEqualTo(1);
        String payload = records.iterator().next().value();
        assertThat(payload).contains("generatedByBot");
        assertThat(payload).contains("true");

        consumer.close();
    }
}
