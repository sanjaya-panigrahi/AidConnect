package com.chatassist.common.dto;

import java.time.LocalDate;

public record DailyChatPeerSummary(
        String username,
        LocalDate date,
        long chatPeerCount
) {
}

