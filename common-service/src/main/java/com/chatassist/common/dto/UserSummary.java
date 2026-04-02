package com.chatassist.common.dto;

import java.time.Instant;

public record UserSummary(
        Long id,
        String username,
        String firstName,
        String lastName,
        String email,
        boolean bot,
        boolean online,
        Instant lastActive
) {
}
