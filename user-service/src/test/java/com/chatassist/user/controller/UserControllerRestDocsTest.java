package com.chatassist.user.controller;

import com.chatassist.common.dto.AssistantSummary;
import com.chatassist.common.dto.AuthResponse;
import com.chatassist.common.dto.LoginRequest;
import com.chatassist.common.dto.RegisterUserRequest;
import com.chatassist.common.dto.UserSummary;
import com.chatassist.user.service.UserMapper;
import com.chatassist.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(RestDocumentationExtension.class)
class UserControllerRestDocsTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        UserService userService = new UserService(null, new UserMapper(), null) {
            @Override
            public AuthResponse register(RegisterUserRequest request) {
                return new AuthResponse(501L, request.username(), request.firstName(), request.lastName(), request.email(), "Registration successful");
            }

            @Override
            public AuthResponse login(LoginRequest request) {
                return new AuthResponse(501L, request.username(), "Alex", "Turner", "alex@example.com", "Login successful");
            }

            @Override
            public List<UserSummary> listUsers(String excludeUsername) {
                return List.of(new UserSummary(
                        701L,
                        "alex",
                        "Alex",
                        "Turner",
                        "alex@example.com",
                        false,
                        true,
                        Instant.parse("2026-03-31T16:40:00Z")
                ));
            }

            @Override
            public List<AssistantSummary> listAssistants() {
                return List.of(
                        new AssistantSummary(-1L, "bot", "General Assistance", "", true),
                        new AssistantSummary(-2L, "aid", "Aid Assistance", "", true)
                );
            }

            @Override
            public UserSummary findByUsername(String username) {
                return new UserSummary(
                        702L,
                        username,
                        "Jordan",
                        "Lee",
                        "jordan@example.com",
                        false,
                        false,
                        Instant.parse("2026-03-31T15:30:00Z")
                );
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService))
                .setControllerAdvice(new UserControllerAdvice())
                .setValidator(validator)
                .apply(documentationConfiguration(restDocumentation))
                .build();
    }

    @Test
    void documentRegister() throws Exception {
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Alex",
                                  "lastName": "Turner",
                                  "username": "alex",
                                  "password": "Password1!",
                                  "email": "alex@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andDo(document("user-register",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("firstName").type(JsonFieldType.STRING).description("User first name."),
                                fieldWithPath("lastName").type(JsonFieldType.STRING).description("User last name."),
                                fieldWithPath("username").type(JsonFieldType.STRING).description("Unique username."),
                                fieldWithPath("password").type(JsonFieldType.STRING).description("Account password."),
                                fieldWithPath("email").type(JsonFieldType.STRING).description("User email address.")
                        ),
                        responseFields(
                                fieldWithPath("userId").type(JsonFieldType.NUMBER).description("Created user ID."),
                                fieldWithPath("username").type(JsonFieldType.STRING).description("Username."),
                                fieldWithPath("firstName").type(JsonFieldType.STRING).description("First name."),
                                fieldWithPath("lastName").type(JsonFieldType.STRING).description("Last name."),
                                fieldWithPath("email").type(JsonFieldType.STRING).description("Email."),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("Result message.")
                        )));
    }

    @Test
    void documentLogin() throws Exception {
        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alex",
                                  "password": "Password1!"
                                }
                                """))
                .andExpect(status().isOk())
                .andDo(document("user-login",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("username").type(JsonFieldType.STRING).description("Username."),
                                fieldWithPath("password").type(JsonFieldType.STRING).description("Password.")
                        ),
                        responseFields(
                                fieldWithPath("userId").type(JsonFieldType.NUMBER).description("User ID."),
                                fieldWithPath("username").type(JsonFieldType.STRING).description("Username."),
                                fieldWithPath("firstName").type(JsonFieldType.STRING).description("First name."),
                                fieldWithPath("lastName").type(JsonFieldType.STRING).description("Last name."),
                                fieldWithPath("email").type(JsonFieldType.STRING).description("Email."),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("Result message.")
                        )));
    }

    @Test
    void documentListUsers() throws Exception {
        mockMvc.perform(get("/api/users")
                        .param("excludeUsername", "bot"))
                .andExpect(status().isOk())
                .andDo(document("user-list-users",
                        preprocessResponse(prettyPrint()),
                        queryParameters(
                                parameterWithName("excludeUsername").description("Optional username to exclude from results.")
                        ),
                        responseFields(
                                fieldWithPath("[].id").type(JsonFieldType.NUMBER).description("User ID."),
                                fieldWithPath("[].username").type(JsonFieldType.STRING).description("Username."),
                                fieldWithPath("[].firstName").type(JsonFieldType.STRING).description("First name."),
                                fieldWithPath("[].lastName").type(JsonFieldType.STRING).description("Last name."),
                                fieldWithPath("[].email").type(JsonFieldType.STRING).description("Email."),
                                fieldWithPath("[].bot").type(JsonFieldType.BOOLEAN).description("Whether this user is a bot account."),
                                fieldWithPath("[].online").type(JsonFieldType.BOOLEAN).description("Current online status."),
                                fieldWithPath("[].lastActive").type(JsonFieldType.NUMBER).optional().description("Last activity timestamp when available.")
                        )));
    }

    @Test
    void documentAssistants() throws Exception {
        mockMvc.perform(get("/api/users/assistants"))
                .andExpect(status().isOk())
                .andDo(document("user-list-assistants",
                        preprocessResponse(prettyPrint()),
                        responseFields(
                                fieldWithPath("[].id").type(JsonFieldType.NUMBER).description("Assistant user ID."),
                                fieldWithPath("[].username").type(JsonFieldType.STRING).description("Assistant username."),
                                fieldWithPath("[].firstName").type(JsonFieldType.STRING).description("Assistant display first name."),
                                fieldWithPath("[].lastName").type(JsonFieldType.STRING).description("Assistant display last name."),
                                fieldWithPath("[].online").type(JsonFieldType.BOOLEAN).description("Assistant availability state.")
                        )));
    }

    @Test
    void documentValidationErrorContract() throws Exception {
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
                .andDo(document("user-validation-error",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        responseFields(
                                fieldWithPath("timestamp").description("Error timestamp."),
                                fieldWithPath("status").description("HTTP status code."),
                                fieldWithPath("error").description("HTTP status reason phrase."),
                                fieldWithPath("message").description("High-level error message."),
                                subsectionWithPath("fieldErrors").description("Field-level validation errors keyed by field name.")
                        )));
    }
}

