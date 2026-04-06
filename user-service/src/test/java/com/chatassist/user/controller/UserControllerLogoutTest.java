package com.chatassist.user.controller;

import com.chatassist.user.service.AuthSessionService;
import com.chatassist.user.service.UserMapper;
import com.chatassist.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerLogoutTest {

    private MockMvc mockMvc;
    private AtomicBoolean logoutCalled;

    @BeforeEach
    void setUp() {
        logoutCalled = new AtomicBoolean(false);

        UserService userService = new UserService(null, new UserMapper(), null) {
            @Override
            public void logout(String username) {
                logoutCalled.set(true);
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService, new AuthSessionService()))
                .setControllerAdvice(new UserControllerAdvice())
                .build();
    }

    @Test
    @DisplayName("logout should clear session cookies")
    void logoutShouldClearSessionCookies() throws Exception {
        mockMvc.perform(post("/api/users/logout")
                        .header("X-Username", "alex"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("SESSION"))
                .andExpect(cookie().value("SESSION", ""))
                .andExpect(cookie().maxAge("SESSION", 0))
                .andExpect(cookie().exists("JSESSIONID"))
                .andExpect(cookie().value("JSESSIONID", ""))
                .andExpect(cookie().maxAge("JSESSIONID", 0));

        assertThat(logoutCalled.get()).isTrue();
    }
}

