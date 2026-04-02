package com.chatassist.common.dto;

public record AuthResponse(
        Long userId,
        String username,
        String firstName,
        String lastName,
        String email,
        String message
) {
}
