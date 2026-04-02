package com.chatassist.user.controller;

import com.chatassist.common.dto.AuthResponse;
import com.chatassist.common.dto.LoginRequest;
import com.chatassist.common.dto.RegisterUserRequest;
import com.chatassist.user.service.UserMapper;
import com.chatassist.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = UserControllerValidationMvcIT.TestApplication.class,
        properties = {
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
        }
)
@AutoConfigureMockMvc
class UserControllerValidationMvcIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("register returns field-level validation contract from full MVC context")
    void registerValidationContract() throws Exception {
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
    @DisplayName("register maps ResponseStatusException to API error contract from full MVC context")
    void registerConflictContract() throws Exception {
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Jane",
                                  "lastName": "Smith",
                                  "username": "conflict-user",
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

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({UserController.class, UserControllerAdvice.class})
    static class TestApplication {

        @Bean
        UserService userService() {
            return new UserService(null, new UserMapper(), null) {
                @Override
                public AuthResponse register(RegisterUserRequest request) {
                    if ("conflict-user".equals(request.username())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
                    }
                    throw new AssertionError("register should not be called for invalid payloads");
                }

                @Override
                public AuthResponse login(LoginRequest request) {
                    throw new AssertionError("login is not expected in this test class");
                }
            };
        }
    }
}

