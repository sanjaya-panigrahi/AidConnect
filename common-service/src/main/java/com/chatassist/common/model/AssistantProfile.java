package com.chatassist.common.model;

import com.chatassist.common.dto.AssistantSummary;
import com.chatassist.common.dto.UserSummary;

import java.util.Arrays;
import java.util.List;

public enum AssistantProfile {
    BOT(1001L, "bot", "General Assistance", "", "bot@chatassist.com"),
    AID(1002L, "aid", "Aid Assistance",     "", "aid@chatassist.com");

    private final Long id;
    private final String username;
    private final String firstName;
    private final String lastName;
    private final String email;

    AssistantProfile(Long id, String username, String firstName, String lastName, String email) {
        this.id = id;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
    }

    public AssistantSummary toAssistantSummary() {
        return new AssistantSummary(id, username, firstName, lastName, true);
    }

    public UserSummary toUserSummary() {
        return new UserSummary(id, username, firstName, lastName, email, true, true, null);
    }

    public static UserSummary byUsername(String username) {
        return Arrays.stream(values())
                .filter(profile -> profile.username.equalsIgnoreCase(username))
                .findFirst()
                .map(AssistantProfile::toUserSummary)
                .orElse(null);
    }

    public static List<AssistantSummary> all() {
        return Arrays.stream(values())
                .map(AssistantProfile::toAssistantSummary)
                .toList();
    }
}

