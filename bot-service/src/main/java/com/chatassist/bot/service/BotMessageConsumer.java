package com.chatassist.bot.service;

import com.chatassist.common.dto.ChatMessageEvent;
import com.chatassist.common.dto.ChatMessageRequest;
import com.chatassist.common.dto.ChatMessageResponse;
import com.chatassist.common.model.AssistantProfile;
import com.chatassist.common.model.MessageStatus;
import com.chatassist.common.model.MessageType;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class BotMessageConsumer {

    private final BotReplyClient replyClient;
    private final AiAssistantService aiAssistantService;
    private final ChatHistoryClient chatHistoryClient;

    public BotMessageConsumer(BotReplyClient replyClient,
                              AiAssistantService aiAssistantService,
                              ChatHistoryClient chatHistoryClient) {
        this.replyClient = replyClient;
        this.aiAssistantService = aiAssistantService;
        this.chatHistoryClient = chatHistoryClient;
    }

    @KafkaListener(topics = "chat-messages", groupId = "bot-service")
    public void consume(ChatMessageEvent event) {
        boolean directBotChat = "bot".equalsIgnoreCase(event.receiverUsername());
        boolean botMention = containsBotMention(event.content());
        if (event.generatedByBot() || (!directBotChat && !botMention)) {
            return;
        }

        var botUser = AssistantProfile.BOT.toUserSummary();
        String prompt = sanitizePrompt(event.content());

        // Warm sessions return from in-memory cache (no HTTP call).
        // Cold start fetches once from chat-service and primes the cache.
        List<ChatMessageResponse> history = chatHistoryClient
                .getConversationHistory(event.senderUsername(), botUser.username());

        String reply = aiAssistantService.reply(prompt, history);

        ChatMessageRequest request = new ChatMessageRequest(
                botUser.id(),
                botUser.username(),
                event.senderId(),
                event.senderUsername(),
                reply,
                MessageType.BOT,
                // When @bot was mentioned in a user-to-user chat, contextUsername carries the
                // original peer so the reply surfaces in that conversation thread too.
                event.contextUsername()
        );
        replyClient.send(request);

        // Keep the in-memory cache current so the next follow-up
        // doesn't need another round-trip to chat-service.
        ChatMessageResponse userMsg = new ChatMessageResponse(
                event.id(), event.senderId(), event.senderUsername(),
                botUser.id(), botUser.username(), event.content(),
                MessageType.BOT, MessageStatus.DELIVERED,
                event.sentAt(), event.deliveredAt(), null, null
        );
        ChatMessageResponse botMsg = new ChatMessageResponse(
                null, botUser.id(), botUser.username(),
                event.senderId(), event.senderUsername(), reply,
                MessageType.BOT, MessageStatus.DELIVERED,
                Instant.now(), Instant.now(), null, null
        );
        chatHistoryClient.appendExchange(event.senderUsername(), userMsg, botMsg);
    }

    private boolean containsBotMention(String content) {
        return content != null && content.toLowerCase(Locale.ROOT).contains("@bot");
    }

    private String sanitizePrompt(String content) {
        if (content == null || content.isBlank()) {
            return "Hello";
        }
        String cleaned = content.replaceAll("(?i)@bot", "").trim();
        return cleaned.isEmpty() ? content.trim() : cleaned;
    }
}
