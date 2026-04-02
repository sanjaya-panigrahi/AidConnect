package com.chatassist.bot.service;

import com.chatassist.bot.config.RedisCacheConfig;
import com.chatassist.common.dto.ChatMessageResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fetches and caches per-user conversation history in Redis.
 *
 * <p>Cold start  – fetches from chat-service once, stores with 60-min sliding TTL.
 * <p>Warm session – reads directly from Redis (zero HTTP calls).
 * <p>appendExchange – atomic WATCH/MULTI/EXEC keeps the window current after every reply.
 */
@Component
public class ChatHistoryClient {

    private static final Logger log = LoggerFactory.getLogger(ChatHistoryClient.class);

    /**
     * Maximum messages kept per user. Matches MAX_HISTORY_MESSAGES in AiAssistantService
     * so the cache stores exactly what the LLM will use — no over-fetch waste.
     */
    static final int MAX_CACHED_MESSAGES = 20;

    private static final TypeReference<List<ChatMessageResponse>> LIST_TYPE = new TypeReference<>() {};
    private static final int APPEND_MAX_RETRIES = 3;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String chatServiceBaseUrl;

    public ChatHistoryClient(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            RestClient restClient,
            @LoadBalanced
            @Qualifier("loadBalancedRestClientBuilder")
            RestClient.Builder loadBalancedRestClientBuilder,
            @Value("${chatassist.discovery.enabled:true}") boolean discoveryEnabled,
            @Value("${services.chat-service.base-url}") String chatServiceBaseUrl) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper.copy()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.restClient = discoveryEnabled ? loadBalancedRestClientBuilder.build() : restClient;
        this.chatServiceBaseUrl = discoveryEnabled ? "http://chat-service" : chatServiceBaseUrl;
    }

    /**
     * Returns recent conversation history. Warm sessions hit Redis only; cold starts
     * fetch from chat-service once and prime the cache.
     */
    public List<ChatMessageResponse> getConversationHistory(String userA, String userB) {
        String key = RedisCacheConfig.HISTORY_KEY_PREFIX + userA;
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                log.debug("Redis cache hit for user={}", userA);
                redisTemplate.expire(key, RedisCacheConfig.HISTORY_TTL);
                return deserialize(cached);
            }
        } catch (DataAccessException e) {
            log.warn("Redis unavailable for history read (user={}): {}", userA, e.getMessage());
        }

        log.debug("Redis cache miss for user={}, fetching from chat-service", userA);
        List<ChatMessageResponse> fetched = fetchFromChatService(userA, userB);
        List<ChatMessageResponse> windowed = tail(fetched, MAX_CACHED_MESSAGES);
        store(key, windowed);
        log.debug("Cached {} messages in Redis for user={}", windowed.size(), userA);
        return windowed;
    }

    /**
     * Atomically appends userMessage + botReply to the Redis sliding window using
     * WATCH / MULTI / EXEC (optimistic locking) so concurrent writes never lose data.
     * Retries up to {@value APPEND_MAX_RETRIES} times on conflict.
     *
     * <p>Note: the botReply stored here carries {@code id=null} because the DB-assigned
     * ID is only available after chat-service persists the reply asynchronously.
     * The null ID is harmless — context building uses only senderUsername + content.
     */
    public void appendExchange(String username, ChatMessageResponse userMessage,
                               ChatMessageResponse botReply) {
        String key = RedisCacheConfig.HISTORY_KEY_PREFIX + username;
        try {
        for (int attempt = 1; attempt <= APPEND_MAX_RETRIES; attempt++) {
            Boolean committed = redisTemplate.execute(new SessionCallback<Boolean>() {
                @Override
                @SuppressWarnings("unchecked")
                public Boolean execute(RedisOperations ops) throws DataAccessException {
                    ops.watch(key);
                    String existing = (String) ops.opsForValue().get(key);
                    List<ChatMessageResponse> messages = existing != null
                            ? new ArrayList<>(deserialize(existing))
                            : new ArrayList<>();
                    messages.add(userMessage);
                    messages.add(botReply);
                    String json = serialize(tail(new ArrayList<>(messages), MAX_CACHED_MESSAGES));
                    if (json == null) {
                        ops.unwatch();
                        return true; // serialization failed — skip silently
                    }
                    ops.multi();
                    ops.opsForValue().set(key, json, RedisCacheConfig.HISTORY_TTL);
                    List<?> result = ops.exec();
                    return result != null && !result.isEmpty();
                }
            });
            if (Boolean.TRUE.equals(committed)) {
                log.debug("Cache updated for user={} (attempt {})", username, attempt);
                return;
            }
            log.debug("Optimistic lock conflict on appendExchange for user={}, retry {}", username, attempt);
        }
        log.warn("Failed to atomically update cache for user={} after {} attempts", username, APPEND_MAX_RETRIES);
        } catch (DataAccessException e) {
            // Keep message handling alive even if Redis is down.
            log.warn("Redis unavailable for appendExchange (user={}): {}", username, e.getMessage());
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private List<ChatMessageResponse> fetchFromChatService(String userA, String userB) {
        try {
            List<ChatMessageResponse> result = restClient.get()
                    .uri(chatServiceBaseUrl + "/api/chats/conversation?userA={userA}&userB={userB}",
                            userA, userB)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return result != null ? result : Collections.emptyList();
        } catch (RestClientException e) {
            log.warn("Failed to fetch conversation history for {} and {}: {}", userA, userB, e.getMessage());
            return Collections.emptyList();
        }
    }

    private void store(String key, List<ChatMessageResponse> messages) {
        String json = serialize(messages);
        if (json != null) {
            try {
                redisTemplate.opsForValue().set(key, json, RedisCacheConfig.HISTORY_TTL);
            } catch (DataAccessException e) {
                log.warn("Redis unavailable while caching history for key={}: {}", key, e.getMessage());
            }
        }
    }

    private String serialize(List<ChatMessageResponse> messages) {
        try {
            return objectMapper.writeValueAsString(messages);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize conversation history: {}", e.getMessage());
            return null;
        }
    }

    private List<ChatMessageResponse> deserialize(String json) {
        try {
            return objectMapper.readValue(json, LIST_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize conversation history from Redis: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private static <T> List<T> tail(List<T> list, int max) {
        if (list.size() <= max) return list;
        return new ArrayList<>(list.subList(list.size() - max, list.size()));
    }
}
