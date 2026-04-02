package com.chatassist.aid.service;

import com.chatassist.common.dto.ChatMessageEvent;
import com.chatassist.common.dto.ChatMessageRequest;
import com.chatassist.common.dto.ChatMessageResponse;
import com.chatassist.common.model.AssistantProfile;
import com.chatassist.common.model.MessageStatus;
import com.chatassist.common.model.MessageType;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AidMessageConsumer {

    private final AidReplyClient replyClient;
    private final AppointmentAssistantService appointmentAssistantService;
    private final ChatHistoryClient chatHistoryClient;

    public AidMessageConsumer(AidReplyClient replyClient,
                              AppointmentAssistantService appointmentAssistantService,
                              ChatHistoryClient chatHistoryClient) {
        this.replyClient = replyClient;
        this.appointmentAssistantService = appointmentAssistantService;
        this.chatHistoryClient = chatHistoryClient;
    }

    @KafkaListener(topics = "chat-messages", groupId = "aid-service")
    public void consume(ChatMessageEvent event) {
        boolean directAidChat = "aid".equalsIgnoreCase(event.receiverUsername());
        boolean aidMentionInUserChat = !directAidChat && containsAidMention(event.content());

        if (event.generatedByBot() || (!directAidChat && !aidMentionInUserChat)) {
            return;
        }

        var aidBot = AssistantProfile.AID.toUserSummary();
        String prompt = sanitizePrompt(event.content());
        String reply = appointmentAssistantService.respond(event.senderUsername(), prompt);

        // When @aid was mentioned in a user-to-user chat, contextUsername carries the
        // original peer so the reply surfaces in that conversation thread too.
        // For direct aid chats this is null, keeping the thread private.
        replyClient.send(new ChatMessageRequest(
                aidBot.id(),
                aidBot.username(),
                event.senderId(),
                event.senderUsername(),
                reply,
                MessageType.BOT,
                event.contextUsername()
        ));

        ChatMessageResponse userMsg = new ChatMessageResponse(
                event.id(), event.senderId(), event.senderUsername(),
                aidBot.id(), aidBot.username(), event.content(),
                MessageType.BOT, MessageStatus.DELIVERED,
                event.sentAt(), event.deliveredAt(), null, null
        );
        ChatMessageResponse aidMsg = new ChatMessageResponse(
                null, aidBot.id(), aidBot.username(),
                event.senderId(), event.senderUsername(), reply,
                MessageType.BOT, MessageStatus.DELIVERED,
                Instant.now(), Instant.now(), null, null
        );
        chatHistoryClient.appendExchange(event.senderUsername(), userMsg, aidMsg);
    }

    private boolean containsAidMention(String content) {
        return content != null && content.toLowerCase(java.util.Locale.ROOT).contains("@aid");
    }

    private String sanitizePrompt(String content) {
        if (content == null || content.isBlank()) {
            return "Hello";
        }
        String cleaned = content.replaceAll("(?i)@aid", "").trim();
        return cleaned.isEmpty() ? content.trim() : cleaned;
    }
}
