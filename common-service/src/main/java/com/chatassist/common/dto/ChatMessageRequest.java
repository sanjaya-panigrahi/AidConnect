package com.chatassist.common.dto;

import com.chatassist.common.model.MessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ChatMessageRequest(
        @NotNull(message = "Sender id is required.")
        @Positive(message = "Sender id must be a positive number.")
        Long senderId,

        @NotBlank(message = "Sender username is required.")
        @Size(min = 3, max = 50, message = "Sender username must be between 3 and 50 characters.")
        String senderUsername,

        @NotNull(message = "Receiver id is required.")
        @Positive(message = "Receiver id must be a positive number.")
        Long receiverId,

        @NotBlank(message = "Receiver username is required.")
        @Size(min = 3, max = 50, message = "Receiver username must be between 3 and 50 characters.")
        String receiverUsername,

        @NotBlank(message = "Message content is required.")
        @Size(max = 4000, message = "Message content must be 4000 characters or fewer.")
        String content,

        @NotNull(message = "Message type is required.")
        MessageType messageType,
        /* When an assistant replies to a mention inside a user-to-user conversation, this field
           holds the other participant's username (e.g. userB when userA mentioned @aid in an
           userA↔userB chat). The reply will then be surfaced in both the assistant thread
           and the original user conversation. */
        @Size(max = 50, message = "Context username must be 50 characters or fewer.")
        String contextUsername
) {
}
