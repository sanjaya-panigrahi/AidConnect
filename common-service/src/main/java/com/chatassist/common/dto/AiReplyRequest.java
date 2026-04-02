package com.chatassist.common.dto;

public record AiReplyRequest(
        Long originalMessageId,
        Long senderId,
        String senderUsername,
        Long receiverId,
        String receiverUsername,
        String prompt
) {
}
