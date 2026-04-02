package com.chatassist.common.dto;

import com.chatassist.common.model.MessageStatus;
import com.chatassist.common.model.MessageType;

import java.time.Instant;

public record ChatMessageResponse(
        Long id,
        Long senderId,
        String senderUsername,
        Long receiverId,
        String receiverUsername,
        String content,
        MessageType messageType,
        MessageStatus status,
        Instant sentAt,
        Instant deliveredAt,
        Instant seenAt,
        String contextUsername
) {
}
