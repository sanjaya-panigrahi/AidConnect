package com.chatassist.bot.service;

import com.chatassist.bot.config.RedisCacheConfig;
import com.chatassist.common.dto.ChatMessageEvent;
import com.chatassist.common.dto.ChatMessageResponse;
import com.chatassist.common.model.MessageStatus;
import com.chatassist.common.model.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link BotMessageConsumer} – the Kafka consumer that
 * processes {@code chat-messages} events and triggers the AI chat reply flow.
 *
 * <p>Containers:
 * <ul>
 *   <li>Redis 7  – stores conversation history for the bot session</li>
 *   <li>Kafka    – real broker so the {@link BotMessageConsumer} listener connects</li>
 * </ul>
 *
 * {@link AiAssistantService}, {@link BotReplyClient} and {@link ChatHistoryClient}
 * are all mocked so no external services or OpenAI are called.
 */
@Testcontainers
@SpringBootTest(properties = {
        // Enable Kafka listener startup for this consumer test
        "spring.kafka.listener.auto-startup=true",
        // Provide a dummy OpenAI key so Spring AI beans initialise
        "spring.ai.openai.api-key=test-key-bot-consumer-it"
})
@ActiveProfiles("test")
@DisplayName("BotMessageConsumer – Kafka Consumer Integration Tests")
class BotKafkaConsumerIntegrationTest {

    // ── Containers ────────────────────────────────────────────────────────────

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
        // Redis
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port",
                () -> redisContainer.getMappedPort(6379).toString());
        registry.add("spring.cache.type", () -> "redis");
        // Kafka
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
        registry.add("spring.kafka.consumer.group-id", () -> "bot-service-consumer-it");
    }

    // ── Mocks ─────────────────────────────────────────────────────────────────

    /** Mocked so no OpenAI call is ever made. */
    @MockitoBean
    private AiAssistantService aiAssistantService;

    /** Mocked so no HTTP call is made back to chat-service. */
    @MockitoBean
    private BotReplyClient botReplyClient;

    /** Mocked so no HTTP call is made to fetch conversation history. */
    @MockitoBean
    private ChatHistoryClient chatHistoryClient;

    // ── Beans ─────────────────────────────────────────────────────────────────

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // ── Setup ─────────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        // Reset Redis
        var conn = stringRedisTemplate.getConnectionFactory().getConnection();
        conn.serverCommands().flushAll();
        conn.close();

        // Default mock stubs
        when(aiAssistantService.reply(anyString(), anyList()))
                .thenReturn("Hello! How can I help?");
        when(chatHistoryClient.getConversationHistory(anyString(), anyString()))
                .thenReturn(Collections.emptyList());
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
        assertThat(redisContainer.isRunning()).isTrue();
        assertThat(kafkaContainer.isRunning()).isTrue();
    }

    @Test
    @DisplayName("Consumer should invoke AiAssistantService when a direct bot message arrives")
    void consumerShouldProcessDirectBotMessage() {
        // Arrange – message addressed directly to "bot"
        ChatMessageEvent event = new ChatMessageEvent(
                1L, 10L, "alice", 999L, "bot", null,
                "Hello bot, how are you?",
                MessageType.USER, MessageStatus.DELIVERED,
                Instant.now(), Instant.now(), null, false
        );

        KafkaTemplate<String, ChatMessageEvent> producer = buildProducer();
        producer.send("chat-messages", event.receiverUsername(), event);
        producer.flush();

        // Assert – AiAssistantService.reply() is called
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        verify(aiAssistantService, atLeastOnce()).reply(anyString(), any())
                );
    }

    @Test
    @DisplayName("Consumer should call BotReplyClient after generating the AI reply")
    void consumerShouldSendReplyViaBotReplyClient() {
        // Arrange
        ChatMessageEvent event = new ChatMessageEvent(
                2L, 11L, "bob", 999L, "bot", null,
                "Tell me a joke",
                MessageType.USER, MessageStatus.DELIVERED,
                Instant.now(), Instant.now(), null, false
        );

        KafkaTemplate<String, ChatMessageEvent> producer = buildProducer();
        producer.send("chat-messages", event.receiverUsername(), event);
        producer.flush();

        // Assert – BotReplyClient.send() was invoked
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        verify(botReplyClient, atLeastOnce()).send(any())
                );
    }

    @Test
    @DisplayName("Consumer should ignore messages generated by a bot (generatedByBot=true)")
    void consumerShouldIgnoreBotGeneratedMessages() throws Exception {
        // Arrange – event already flagged as bot-generated
        ChatMessageEvent botEvent = new ChatMessageEvent(
                3L, 999L, "bot", 11L, "bob", null,
                "I am a bot reply, ignore me",
                MessageType.BOT, MessageStatus.DELIVERED,
                Instant.now(), Instant.now(), null, true  // generatedByBot = true
        );

        KafkaTemplate<String, ChatMessageEvent> producer = buildProducer();
        producer.send("chat-messages", botEvent.receiverUsername(), botEvent);
        producer.flush();

        // Wait briefly, then confirm no AI call
        TimeUnit.SECONDS.sleep(3);
        verify(aiAssistantService, never()).reply(anyString(), any());
    }

    @Test
    @DisplayName("Consumer should process @bot mention in user-to-user messages")
    void consumerShouldProcessBotMentionInUserChat() {
        // Arrange – user-to-user message that @mentions bot
        ChatMessageEvent event = new ChatMessageEvent(
                4L, 12L, "carol", 13L, "dave", null,
                "@bot what is the weather today?",
                MessageType.USER, MessageStatus.DELIVERED,
                Instant.now(), Instant.now(), null, false
        );

        KafkaTemplate<String, ChatMessageEvent> producer = buildProducer();
        producer.send("chat-messages", event.receiverUsername(), event);
        producer.flush();

        // Assert – consumer triggers AI because content contains @bot
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        verify(aiAssistantService, atLeastOnce()).reply(anyString(), any())
                );
    }

    @Test
    @DisplayName("Consumer should ignore unrelated messages (neither @bot mention nor direct)")
    void consumerShouldIgnoreUnrelatedMessages() throws Exception {
        // Arrange – purely user-to-user message with no bot mention
        ChatMessageEvent event = new ChatMessageEvent(
                5L, 14L, "eve", 15L, "frank", null,
                "Hey Frank, how are you?",
                MessageType.USER, MessageStatus.DELIVERED,
                Instant.now(), Instant.now(), null, false
        );

        KafkaTemplate<String, ChatMessageEvent> producer = buildProducer();
        producer.send("chat-messages", event.receiverUsername(), event);
        producer.flush();

        // Wait briefly, then confirm no AI call
        TimeUnit.SECONDS.sleep(3);
        verify(aiAssistantService, never()).reply(anyString(), any());
    }

    @Test
    @DisplayName("Consumer should append exchange to history after processing a direct bot message")
    void consumerShouldAppendExchangeAfterProcessing() {
        // Arrange
        ChatMessageEvent event = new ChatMessageEvent(
                6L, 16L, "grace", 999L, "bot", null,
                "What time is it?",
                MessageType.USER, MessageStatus.DELIVERED,
                Instant.now(), Instant.now(), null, false
        );

        KafkaTemplate<String, ChatMessageEvent> producer = buildProducer();
        producer.send("chat-messages", event.receiverUsername(), event);
        producer.flush();

        // Assert – appendExchange was called after the reply
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        verify(chatHistoryClient, atLeastOnce())
                                .appendExchange(anyString(),
                                        any(ChatMessageResponse.class),
                                        any(ChatMessageResponse.class))
                );
    }

    @Test
    @DisplayName("Redis integration: bot-svc history keys can be written and read")
    void redisShouldStoreAndRetrieveBotSvcHistoryKeys() {
        String testKey = RedisCacheConfig.HISTORY_KEY_PREFIX + "test-user";
        stringRedisTemplate.opsForValue().set(testKey, "[{\"test\":\"data\"}]",
                RedisCacheConfig.HISTORY_TTL);

        String stored = stringRedisTemplate.opsForValue().get(testKey);
        assertThat(stored).isNotNull().contains("test");
    }
}
