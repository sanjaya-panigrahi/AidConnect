package com.chatassist.common.dto;

public record AssistantSummary(
        Long id,
        String username,
        String firstName,
        String lastName,
        boolean online
) {
}

