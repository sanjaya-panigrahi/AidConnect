package com.chatassist.chat.service;

import com.chatassist.chat.config.RedisCacheConfig;
import com.chatassist.chat.entity.ChatMessage;
import com.chatassist.chat.repository.ChatMessageRepository;
import com.chatassist.common.dto.ChatMessageRequest;
import com.chatassist.common.dto.ChatMessageResponse;
import com.chatassist.common.dto.DailyChatPeerSummary;
import com.chatassist.common.dto.StatusUpdateRequest;
import com.chatassist.common.model.AssistantProfile;
import com.chatassist.common.model.MessageStatus;
import com.chatassist.common.model.MessageType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class ChatMessagingService {
    private static final Logger logger = Logger.getLogger(ChatMessagingService.class.getName());
    private static final String GUEST_USERNAME_PREFIX = "guest-";
    private static final ZoneOffset ACTIVITY_ZONE = ZoneOffset.UTC;

    private final ChatMessageRepository repository;
    private final ChatMessageMapper mapper;
    private final ChatEventPublisher eventPublisher;
    private final WebSocketNotifier webSocketNotifier;
    private final RestClient restClient;
    private final String userServiceBaseUrl;

    // Field-injected so existing constructors don't need changing.
    @Autowired(required = false)
    private CacheManager cacheManager;

    public ChatMessagingService(ChatMessageRepository repository, ChatMessageMapper mapper,
                                ChatEventPublisher eventPublisher, WebSocketNotifier webSocketNotifier,
                                RestClient restClient) {
        this(repository, mapper, eventPublisher, webSocketNotifier, restClient,
                RestClient.builder(), false, "http://localhost:8081");
    }

    @Autowired
    public ChatMessagingService(ChatMessageRepository repository, ChatMessageMapper mapper,
                                ChatEventPublisher eventPublisher, WebSocketNotifier webSocketNotifier,
                                RestClient restClient,
                                @LoadBalanced RestClient.Builder loadBalancedRestClientBuilder,
                                @Value("${chatassist.discovery.enabled:true}") boolean discoveryEnabled,
                                @Value("${services.user-service.base-url}") String userServiceBaseUrl) {
        this.repository = repository;
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
        this.webSocketNotifier = webSocketNotifier;
        this.restClient = discoveryEnabled ? loadBalancedRestClientBuilder.build() : restClient;
        this.userServiceBaseUrl = discoveryEnabled ? "http://user-service" : userServiceBaseUrl;
    }

    /**
     * Sends a message and evicts ONLY the affected conversation pair from the
     * cache (not allEntries) so unrelated conversations stay warm.
     *
     * <p>Routing is resolved first (e.g. @bot mention → targetReceiver = "bot"),
     * then the canonical cache key is computed and evicted programmatically.
     */
    @Transactional
    @CacheEvict(value = RedisCacheConfig.ACTIVITY_TODAY, allEntries = true)
    public ChatMessageResponse send(ChatMessageRequest request, boolean generatedByBot) {
        if (!isGuestUsername(request.senderUsername())) {
            markSenderOnlineAsync(request.senderUsername());
        }

        boolean hasBotMention = containsBotMention(request.content());
        boolean directBotTarget = "bot".equalsIgnoreCase(request.receiverUsername());
        boolean hasAidMention = containsAidMention(request.content());
        boolean directAidTarget = "aid".equalsIgnoreCase(request.receiverUsername());

        Long targetReceiverId = request.receiverId();
        String targetReceiverUsername = request.receiverUsername();
        MessageType targetMessageType = request.messageType();

        // When routing due to an @mention, remember the original receiver so the
        // bot/aid reply can surface in the original user-to-user conversation thread.
        String mentionContext = null;

        if (hasBotMention && !directBotTarget) {
            mentionContext = request.receiverUsername();
            var bot = AssistantProfile.BOT.toUserSummary();
            targetReceiverId = bot.id();
            targetReceiverUsername = bot.username();
            targetMessageType = MessageType.BOT;
        }

        if (hasAidMention && !directAidTarget) {
            mentionContext = request.receiverUsername();
            var aid = AssistantProfile.AID.toUserSummary();
            targetReceiverId = aid.id();
            targetReceiverUsername = aid.username();
            targetMessageType = MessageType.BOT;
        }

        Instant now = Instant.now();
        ChatMessage message = new ChatMessage(
                request.senderId(),
                request.senderUsername(),
                targetReceiverId,
                targetReceiverUsername,
                request.content(),
                targetMessageType,
                MessageStatus.DELIVERED,
                now,
                now
        );
        // For @mention routing: store original receiver so the bot/aid reply can
        // reference it as contextUsername and surface in the original conversation.
        // For direct bot/aid chats: keep null.
        // For bot/aid replies that already carry a contextUsername: allow through.
        if (mentionContext != null) {
            message.setContextUsername(mentionContext);
        } else if (request.contextUsername() != null
                && !"aid".equalsIgnoreCase(targetReceiverUsername)
                && !"bot".equalsIgnoreCase(targetReceiverUsername)) {
            message.setContextUsername(request.contextUsername());
        }
        ChatMessage saved = repository.save(message);
        ChatMessageResponse response = mapper.toResponse(saved);
        webSocketNotifier.notifyMessage(response);
        eventPublisher.publish(mapper.toEvent(saved, generatedByBot));

        // Evict only the specific sender↔receiver pair, not the entire cache.
        evictConversation(request.senderUsername(), targetReceiverUsername);
        return response;
    }

    /**
     * Loads conversation history. Result is cached per canonical user pair
     * (key = "userA:userB" sorted lexicographically so both orderings hit same entry).
     */
    @Transactional(readOnly = true)
    @Cacheable(
        value = RedisCacheConfig.CONVERSATIONS,
        key   = "#userA.compareTo(#userB) <= 0 ? #userA + ':' + #userB : #userB + ':' + #userA"
    )
    public List<ChatMessageResponse> getConversation(String userA, String userB) {
        return repository.findConversation(userA, userB)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = RedisCacheConfig.ACTIVITY_TODAY, key = "#username")
    public DailyChatPeerSummary getDailyChatPeerSummary(String username) {
        LocalDate today = LocalDate.now(ACTIVITY_ZONE);
        Instant startInclusive = today.atStartOfDay(ACTIVITY_ZONE).toInstant();
        Instant endExclusive = startInclusive.plusSeconds(24 * 60 * 60);
        long chatPeerCount = repository.countDistinctPeersForDate(username, startInclusive, endExclusive);
        return new DailyChatPeerSummary(username, today, chatPeerCount);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = RedisCacheConfig.ACTIVITY_TODAY, key = "'all'")
    public List<DailyChatPeerSummary> getAllDailyChatPeerSummaries() {
        LocalDate today = LocalDate.now(ACTIVITY_ZONE);
        Instant startInclusive = today.atStartOfDay(ACTIVITY_ZONE).toInstant();
        Instant endExclusive = startInclusive.plusSeconds(24 * 60 * 60);
        return repository.countDistinctPeersForAllUsersOnDate(startInclusive, endExclusive).stream()
                .map(row -> new DailyChatPeerSummary(
                        (String) row[0],
                        today,
                        ((Number) row[1]).longValue()))
                .toList();
    }

    /**
     * Status update (SEEN/DELIVERED) evicts only the specific conversation pair.
     * The message is already loaded from DB here, so sender/receiver are known.
     */
    @Transactional
    public ChatMessageResponse updateStatus(StatusUpdateRequest request) {
        ChatMessage message = repository.findById(request.messageId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));

        if (request.status() == MessageStatus.SEEN) {
            message.markSeen(Instant.now());
        } else if (request.status() == MessageStatus.DELIVERED) {
            message.markDelivered(Instant.now());
        }

        ChatMessageResponse response = mapper.toResponse(message);
        webSocketNotifier.notifyMessage(response);

        // Evict only this conversation pair — sender/receiver are known from the loaded message.
        evictConversation(message.getSenderUsername(), message.getReceiverUsername());
        return response;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Evicts the canonical cache entry for a specific user pair.
     * Key format must match the @Cacheable SpEL expression on getConversation().
     * No-op when cacheManager is not yet injected (e.g. plain-constructor tests).
     */
    private void evictConversation(String userA, String userB) {
        if (cacheManager == null) return;
        String key = userA.compareTo(userB) <= 0
                ? userA + ":" + userB
                : userB + ":" + userA;
        Cache cache = cacheManager.getCache(RedisCacheConfig.CONVERSATIONS);
        if (cache != null) {
            cache.evict(key);
            logger.fine("Evicted conversation cache for pair: " + key);
        }
    }

    @Async
    protected void markSenderOnlineAsync(String username) {
        try {
            restClient.put()
                    .uri(userServiceBaseUrl + "/api/users/{username}/online", username)
                    .header("X-Username", username)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to mark user online: " + username, e);
        }
    }

    private boolean containsBotMention(String content) {
        return content != null && content.toLowerCase(Locale.ROOT).contains("@bot");
    }

    private boolean containsAidMention(String content) {
        return content != null && content.toLowerCase(Locale.ROOT).contains("@aid");
    }

    private boolean isGuestUsername(String username) {
        return username != null && username.toLowerCase(Locale.ROOT).startsWith(GUEST_USERNAME_PREFIX);
    }
}

