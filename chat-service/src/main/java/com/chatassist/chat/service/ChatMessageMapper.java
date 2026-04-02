package com.chatassist.chat.service;

import com.chatassist.chat.entity.ChatMessage;
import com.chatassist.common.dto.ChatMessageEvent;
import com.chatassist.common.dto.ChatMessageResponse;
import org.springframework.stereotype.Component;

@Component
public class ChatMessageMapper {

    public ChatMessageResponse toResponse(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getSenderId(),
                message.getSenderUsername(),
                message.getReceiverId(),
                message.getReceiverUsername(),
                message.getContent(),
                message.getMessageType(),
                message.getStatus(),
                message.getSentAt(),
                message.getDeliveredAt(),
                message.getSeenAt(),
                message.getContextUsername()
        );
    }

    public ChatMessageEvent toEvent(ChatMessage message, boolean generatedByBot) {
        return new ChatMessageEvent(
                message.getId(),
                message.getSenderId(),
                message.getSenderUsername(),
                message.getReceiverId(),
                message.getReceiverUsername(),
                message.getContextUsername(),
                message.getContent(),
                message.getMessageType(),
                message.getStatus(),
                message.getSentAt(),
                message.getDeliveredAt(),
                message.getSeenAt(),
                generatedByBot
        );
    }
}
