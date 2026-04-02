package com.chatassist.common.dto;

import java.time.LocalDate;

public record UserActivitySummary(
        String    username,
        LocalDate date,
        long      loginCount,
        long      logoutCount
) {}

