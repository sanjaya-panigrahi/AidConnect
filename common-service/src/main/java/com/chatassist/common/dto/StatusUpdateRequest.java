package com.chatassist.common.dto;

import com.chatassist.common.model.MessageStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record StatusUpdateRequest(
		@NotNull(message = "Message id is required.")
		@Positive(message = "Message id must be a positive number.")
		Long messageId,

		@NotNull(message = "Message status is required.")
		MessageStatus status
) {
}
