package com.chatassist.user.service;

import com.chatassist.common.security.AuthSessionKeyUtil;
import org.springframework.stereotype.Service;
import jakarta.servlet.http.HttpSession;

@Service
public class AuthSessionService {
    public void initializeAuthenticatedSession(HttpSession session, Long userId, String username) {
        session.setAttribute(AuthSessionKeyUtil.SESSION_USER_ID_ATTRIBUTE, userId);
        session.setAttribute(AuthSessionKeyUtil.SESSION_USERNAME_ATTRIBUTE, username);
        session.setAttribute(AuthSessionKeyUtil.SESSION_AUTH_CREATED_AT_ATTRIBUTE, System.currentTimeMillis());
    }

    public void invalidate(HttpSession session) {
        if (session == null) {
            return;
        }
        try {
            session.invalidate();
        } catch (IllegalStateException ignored) {
            // Already invalidated; treat logout as idempotent.
        }
    }
}

