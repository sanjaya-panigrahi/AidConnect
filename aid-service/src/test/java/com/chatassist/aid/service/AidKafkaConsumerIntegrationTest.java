package com.chatassist.aid.service;

import com.chatassist.aid.repository.DoctorAvailabilityRepository;
import com.chatassist.aid.repository.DoctorRepository;
import com.chatassist.common.dto.ChatMessageEvent;
import com.chatassist.common.model.MessageStatus;
import com.chatassist.common.model.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link AidMessageConsumer} – the Kafka consumer
 * that processes {@code chat-messages} events and triggers the AI appointment flow.
 *
 * <p>Containers:
 * <ul>
 *   <li>MySQL 8.0 – required for JPA context initialisation</li>
 *   <li>Redis 7   – required for {@code DoctorCacheService}</li>
 *   <li>Kafka     – real broker so the {@link AidMessageConsumer} listener connects</li>
 * </ul>
 *
 * {@link AppointmentAssistantService}, {@link AidReplyClient} and
 * {@link ChatHistoryClient} are all mocked so no external services are called.
 */
@Testcontainers
@SpringBootTest(properties = {
        // Enable Kafka listener startup for this consumer test
        "spring.kafka.listener.auto-startup=true",
        // Provide a dummy OpenAI key so Spring AI beans initialise without error
        "spring.ai.openai.api-key=test-key-consumer-it"
})
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("AidMessageConsumer – Kafka Consumer Integration Tests")
class AidKafkaConsumerIntegrationTest {

    // ── Containers ────────────────────────────────────────────────────────────

    @Container
    static final MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("aid_service_db")
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
        registry.add("spring.kafka.consumer.group-id", () -> "aid-service-consumer-it");
    }

    // ── Mocks ─────────────────────────────────────────────────────────────────

    /** Mocked so the AI service never calls OpenAI. */
    @MockitoBean
    private AppointmentAssistantService appointmentAssistantService;

    /** Mocked so no HTTP call is made back to chat-service. */
    @MockitoBean
    private AidReplyClient aidReplyClient;

    /** Mocked so no HTTP call is made to fetch conversation history. */
    @MockitoBean
    private ChatHistoryClient chatHistoryClient;

    // ── Beans ─────────────────────────────────────────────────────────────────


    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private DoctorAvailabilityRepository availabilityRepository;

    // ── Setup ─────────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() throws Exception {
        availabilityRepository.deleteAll();
        doctorRepository.deleteAll();

        // Ensure mock respond() returns a non-null string
        when(appointmentAssistantService.respond(anyString(), anyString()))
                .thenReturn("Test AI reply");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private KafkaTemplate<String, ChatMessageEvent> buildProducer() {
        var props = new java.util.HashMap<String, Object>();
        props.put(org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaContainer.getBootstrapServers());
        props.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringSerializer.class);
        props.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                org.springframework.kafka.support.serializer.JsonSerializer.class);
        props.put(org.springframework.kafka.support.serializer.JsonSerializer.ADD_TYPE_INFO_HEADERS,
                false);
        var factory = new org.springframework.kafka.core.DefaultKafkaProducerFactory<String, ChatMessageEvent>(props);
        return new KafkaTemplate<>(factory);
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
    @DisplayName("Consumer should call AppointmentAssistantService when a direct aid message arrives")
    void consumerShouldProcessDirectAidMessage() {
        // Arrange – message addressed directly to "aid"
        ChatMessageEvent event = new ChatMessageEvent(
                1L, 10L, "alice", 999L, "aid", null,
                "I need an appointment",
                MessageType.USER, MessageStatus.DELIVERED,
                Instant.now(), Instant.now(), null, false
        );

        KafkaTemplate<String, ChatMessageEvent> producer = buildProducer();
        producer.send("chat-messages", event.receiverUsername(), event);
        producer.flush();

        // Assert – wait up to 30 s for the consumer to process the event
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        verify(appointmentAssistantService, atLeastOnce())
                                .respond(anyString(), anyString())
                );
    }

    @Test
    @DisplayName("Consumer should call AidReplyClient after generating the AI reply")
    void consumerShouldSendReplyViaReplyClient() {
        // Arrange
        ChatMessageEvent event = new ChatMessageEvent(
                2L, 11L, "bob", 999L, "aid", null,
                "Book me with Dr. Smith",
                MessageType.USER, MessageStatus.DELIVERED,
                Instant.now(), Instant.now(), null, false
        );

        KafkaTemplate<String, ChatMessageEvent> producer = buildProducer();
        producer.send("chat-messages", event.receiverUsername(), event);
        producer.flush();

        // Assert – AidReplyClient.send() was invoked
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        verify(aidReplyClient, atLeastOnce()).send(any())
                );
    }

    @Test
    @DisplayName("Consumer should ignore messages generated by a bot (generatedByBot=true)")
    void consumerShouldIgnoreBotGeneratedMessages() throws Exception {
        // Arrange – event flagged as bot-generated
        ChatMessageEvent botEvent = new ChatMessageEvent(
                3L, 999L, "aid", 11L, "bob", null,
                "I am a bot reply",
                MessageType.BOT, MessageStatus.DELIVERED,
                Instant.now(), Instant.now(), null, true // generatedByBot = true
        );

        KafkaTemplate<String, ChatMessageEvent> producer = buildProducer();
        producer.send("chat-messages", botEvent.receiverUsername(), botEvent);
        producer.flush();

        // Wait briefly, then assert no AI call was made
        TimeUnit.SECONDS.sleep(3);
        verify(appointmentAssistantService, org.mockito.Mockito.never())
                .respond(anyString(), anyString());
    }

    @Test
    @DisplayName("Consumer should process @aid mention in user-to-user messages")
    void consumerShouldProcessAidMentionInUserChat() {
        // Arrange – message is a user-to-user chat that @mentions aid
        ChatMessageEvent event = new ChatMessageEvent(
                4L, 12L, "carol", 13L, "dave", null,
                "@aid I need to see a cardiologist",
                MessageType.USER, MessageStatus.DELIVERED,
                Instant.now(), Instant.now(), null, false
        );

        KafkaTemplate<String, ChatMessageEvent> producer = buildProducer();
        producer.send("chat-messages", event.receiverUsername(), event);
        producer.flush();

        // Assert – consumer triggers AI because content contains @aid
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        verify(appointmentAssistantService, atLeastOnce())
                                .respond(anyString(), anyString())
                );
    }
}
