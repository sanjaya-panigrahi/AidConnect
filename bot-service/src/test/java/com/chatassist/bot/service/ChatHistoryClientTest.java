package com.chatassist.bot.service;

import com.chatassist.common.dto.ChatMessageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chatassist.common.model.MessageStatus;
import com.chatassist.common.model.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatHistoryClientTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private RestClient restClient;
    @Mock
    private RestClient.RequestHeadersUriSpec uriSpec;
    @Mock
    private RestClient.RequestHeadersSpec headersSpec;
    @Mock
    private RestClient.ResponseSpec responseSpec;
    @Mock
    private RestClient.Builder loadBalancedBuilder;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Map<String, String> redis = new ConcurrentHashMap<>();

    private ChatHistoryClient client;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(any())).thenAnswer(invocation -> redis.get(invocation.getArgument(0)));
        lenient().doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = invocation.getArgument(1);
            redis.put(key, value);
            return null;
        }).when(valueOperations).set(any(String.class), any(String.class), any(java.time.Duration.class));
        lenient().when(redisTemplate.expire(any(String.class), any(java.time.Duration.class))).thenReturn(true);

        // Simulate SessionCallback (WATCH/MULTI/EXEC) by running the callback
        // against the same in-memory redis map so appendExchange tests work correctly.
        lenient().when(redisTemplate.execute(any(org.springframework.data.redis.core.SessionCallback.class)))
                .thenAnswer(invocation -> {
                    org.springframework.data.redis.core.SessionCallback<?> cb = invocation.getArgument(0);
                    org.springframework.data.redis.core.RedisOperations<String, String> txOps = buildTxOps();
                    try {
                        return cb.execute(txOps);
                    } catch (Exception e) {
                        return false;
                    }
                });

        lenient().when(uriSpec.uri(any(String.class), any(), any())).thenReturn(headersSpec);
        lenient().when(headersSpec.retrieve()).thenReturn(responseSpec);
        lenient().when(restClient.get()).thenReturn(uriSpec);

        client = new ChatHistoryClient(
                redisTemplate,
                objectMapper,
                restClient,
                loadBalancedBuilder,
                false,
                "http://chat-service"
        );
    }

    /** Builds a minimal RedisOperations stub that delegates to the in-memory redis map. */
    @SuppressWarnings("unchecked")
    private org.springframework.data.redis.core.RedisOperations<String, String> buildTxOps() {
        org.springframework.data.redis.core.RedisOperations<String, String> ops =
                org.mockito.Mockito.mock(org.springframework.data.redis.core.RedisOperations.class);
        org.springframework.data.redis.core.ValueOperations<String, String> vops =
                org.mockito.Mockito.mock(org.springframework.data.redis.core.ValueOperations.class);
        org.mockito.Mockito.when(ops.opsForValue()).thenReturn(vops);
        org.mockito.Mockito.when(vops.get(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(i -> redis.get(i.getArgument(0)));
        org.mockito.Mockito.doAnswer(i -> { redis.put(i.getArgument(0), i.getArgument(1)); return null; })
                .when(vops).set(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(java.time.Duration.class));
        org.mockito.Mockito.when(ops.exec()).thenReturn(java.util.List.of("OK")); // non-null = committed
        return ops;
    }

    // ── Cold-start fetch ─────────────────────────────────────────────────────

    @Test
    void getConversationHistory_returnsMessages_whenChatServiceResponds() {
        List<ChatMessageResponse> remoteHistory = List.of(
                new ChatMessageResponse(1L, 100L, "alice", 2L, "bot", "hello",
                        MessageType.BOT, MessageStatus.DELIVERED, Instant.now(), Instant.now(), null, null)
        );
        when(responseSpec.body(any(org.springframework.core.ParameterizedTypeReference.class))).thenReturn(remoteHistory);

        List<ChatMessageResponse> history = client.getConversationHistory("alice", "bot");

        assertThat(history).hasSize(1);
        ChatMessageResponse first = history.get(0);
        assertThat(first.senderUsername()).isEqualTo("alice");
        assertThat(first.receiverUsername()).isEqualTo("bot");
        assertThat(first.content()).isEqualTo("hello");
        assertThat(first.messageType()).isEqualTo(MessageType.BOT);
        assertThat(first.status()).isEqualTo(MessageStatus.DELIVERED);
        verify(restClient).get();
        verify(valueOperations).set(eq("bot-svc:history:alice"), any(String.class), any(java.time.Duration.class));
    }

    @Test
    void getConversationHistory_returnsEmptyList_whenChatServiceFails() {
        when(responseSpec.body(any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenThrow(new org.springframework.web.client.RestClientException("boom"));

        List<ChatMessageResponse> history = client.getConversationHistory("alice", "bot");

        assertThat(history).isEmpty();
        verify(restClient).get();
    }

    // ── Cache hit (warm session — no HTTP call) ───────────────────────────────

    @Test
    void getConversationHistory_servesFromCache_onSecondCall() {
        List<ChatMessageResponse> remoteHistory = List.of(
                new ChatMessageResponse(1L, 100L, "alice", 2L, "bot", "hello",
                        MessageType.BOT, MessageStatus.DELIVERED, Instant.now(), Instant.now(), null, null)
        );
        when(responseSpec.body(any(org.springframework.core.ParameterizedTypeReference.class))).thenReturn(remoteHistory);

        client.getConversationHistory("alice", "bot"); // cold — primes cache
        List<ChatMessageResponse> second = client.getConversationHistory("alice", "bot"); // warm

        assertThat(second).hasSize(1);
        verify(restClient).get(); // only first call hits remote
    }

    // ── appendExchange ────────────────────────────────────────────────────────

    @Test
    void appendExchange_addsMessagesToCache_andSubsequentGetReturnsUpdated() {
        List<ChatMessageResponse> remoteHistory = List.of(
                new ChatMessageResponse(1L, 100L, "alice", 2L, "bot", "hello",
                        MessageType.BOT, MessageStatus.DELIVERED, Instant.now(), Instant.now(), null, null)
        );
        when(responseSpec.body(any(org.springframework.core.ParameterizedTypeReference.class))).thenReturn(remoteHistory);
        client.getConversationHistory("alice", "bot");

        // Simulate the consumer appending a new exchange.
        ChatMessageResponse userMsg = new ChatMessageResponse(
                2L, 100L, "alice", 2L, "bot", "follow-up?",
                MessageType.BOT, MessageStatus.DELIVERED, Instant.now(), Instant.now(), null, null);
        ChatMessageResponse botMsg = new ChatMessageResponse(
                3L, 2L, "bot", 100L, "alice", "Sure, here you go.",
                MessageType.BOT, MessageStatus.DELIVERED, Instant.now(), Instant.now(), null, null);
        client.appendExchange("alice", userMsg, botMsg);

        // Next getConversationHistory call must include the appended messages.
        List<ChatMessageResponse> updated = client.getConversationHistory("alice", "bot");
        assertThat(updated).hasSize(3);
        assertThat(updated.get(1).content()).isEqualTo("follow-up?");
        assertThat(updated.get(2).content()).isEqualTo("Sure, here you go.");
        verify(restClient).get();
    }

    // ── Sliding window cap ────────────────────────────────────────────────────

    @Test
    void appendExchange_trimsCacheToMaxWindow() {
        when(responseSpec.body(any(org.springframework.core.ParameterizedTypeReference.class))).thenReturn(List.of());
        client.getConversationHistory("alice", "bot"); // prime with empty

        // Append MAX_CACHED_MESSAGES / 2 + 1 exchanges (each exchange = 2 messages)
        int halfMax = ChatHistoryClient.MAX_CACHED_MESSAGES / 2;
        for (int i = 0; i < halfMax + 1; i++) {
            client.appendExchange("alice",
                    new ChatMessageResponse((long) (i * 2), 100L, "alice", 2L, "bot", "q" + i,
                            MessageType.BOT, MessageStatus.DELIVERED, Instant.now(), Instant.now(), null, null),
                    new ChatMessageResponse((long) (i * 2 + 1), 2L, "bot", 100L, "alice", "a" + i,
                            MessageType.BOT, MessageStatus.DELIVERED, Instant.now(), Instant.now(), null, null));
        }

        List<ChatMessageResponse> result = client.getConversationHistory("alice", "bot");
        assertThat(result.size()).isLessThanOrEqualTo(ChatHistoryClient.MAX_CACHED_MESSAGES);
        verify(restClient).get();
    }

    @Test
    void getConversationHistory_usesRedisHit_withoutRemoteCall() throws Exception {
        String key = "bot-svc:history:alice";
        String payload = objectMapper.writeValueAsString(List.of(
                new ChatMessageResponse(5L, 2L, "bot", 100L, "alice", "cached",
                        MessageType.BOT, MessageStatus.DELIVERED, Instant.now(), Instant.now(), null, null)
        ));
        redis.put(key, payload);

        List<ChatMessageResponse> result = client.getConversationHistory("alice", "bot");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).content()).isEqualTo("cached");
        verify(restClient, never()).get();
    }
}
