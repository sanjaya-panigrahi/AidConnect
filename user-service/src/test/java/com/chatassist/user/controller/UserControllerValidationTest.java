package com.chatassist.user.controller;

import com.chatassist.common.dto.AuthResponse;
import com.chatassist.common.dto.LoginRequest;
import com.chatassist.common.dto.RegisterUserRequest;
import com.chatassist.user.service.AuthSessionService;
import com.chatassist.user.service.UserMapper;
import com.chatassist.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerValidationTest {

    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        UserService userService = new UserService(null, new UserMapper(), null) {
            @Override
            public AuthResponse register(RegisterUserRequest request) {
                throw new AssertionError("register should not be called for invalid payloads");
            }

            @Override
            public AuthResponse login(LoginRequest request) {
                throw new AssertionError("login should not be called for invalid payloads");
            }
        };

        mockMvc = buildMockMvc(userService);
    }

    private MockMvc buildMockMvc(UserService userService) {
        return MockMvcBuilders.standaloneSetup(new UserController(userService, new AuthSessionService()))
                .setControllerAdvice(new UserControllerAdvice())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("register rejects invalid payloads with field errors")
    void registerRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "",
                                  "lastName": "   ",
                                  "username": "ab",
                                  "password": "password1",
                                  "email": "invalid-email"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed."))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.fieldErrors.firstName").value("First name is required."))
                .andExpect(jsonPath("$.fieldErrors.lastName").value("Last name is required."))
                .andExpect(jsonPath("$.fieldErrors.username").value("Username must be between 3 and 50 characters."))
                .andExpect(jsonPath("$.fieldErrors.password").value("Password must include uppercase, lowercase, number, and special character."))
                .andExpect(jsonPath("$.fieldErrors.email").value("Email must be a valid email address."));
    }

    @Test
    @DisplayName("login rejects invalid payloads with field errors")
    void loginRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "bad name",
                                  "password": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed."))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.fieldErrors.username").value("Username can contain letters, numbers, dots, underscores, and hyphens only."))
                .andExpect(jsonPath("$.fieldErrors.password").value("Password is required."));
    }

    @Test
    @DisplayName("register maps response status errors to API error response")
    void registerMapsResponseStatusException() throws Exception {
        UserService conflictService = new UserService(null, new UserMapper(), null) {
            @Override
            public AuthResponse register(RegisterUserRequest request) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
            }
        };
        mockMvc = buildMockMvc(conflictService);

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Jane",
                                  "lastName": "Smith",
                                  "username": "janesmith",
                                  "password": "Password1!",
                                  "email": "jane@example.com"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Username already exists"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }
}

