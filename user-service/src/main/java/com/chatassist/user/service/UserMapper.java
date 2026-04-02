package com.chatassist.user.service;

import com.chatassist.common.dto.AuthResponse;
import com.chatassist.common.dto.UserSummary;
import com.chatassist.user.entity.AppUser;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public AuthResponse toAuthResponse(AppUser user, String message) {
        return new AuthResponse(
                user.getId(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                message
        );
    }

    public UserSummary toSummary(AppUser user) {
        return new UserSummary(
                user.getId(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.isBot(),
                user.isOnline(),
                user.getLastActive()
        );
    }
}
