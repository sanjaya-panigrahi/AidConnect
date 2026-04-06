package com.chatassist.chat.controller;

import com.chatassist.chat.service.ChatEventPublisher;
import com.chatassist.chat.service.ChatMessageMapper;
import com.chatassist.chat.service.ChatMessagingService;
import com.chatassist.chat.service.WebSocketNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatControllerValidationTest {

    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        ChatMessagingService chatMessagingService = new ChatMessagingService(
                null,
                new ChatMessageMapper(),
                new ChatEventPublisher(null),
                new WebSocketNotifier(null),
                RestClient.create()
        ) {
            @Override
            public com.chatassist.common.dto.ChatMessageResponse send(com.chatassist.common.dto.ChatMessageRequest request, boolean generatedByBot) {
                throw new AssertionError("send should not be called for invalid payloads");
            }

            @Override
            public com.chatassist.common.dto.ChatMessageResponse updateStatus(com.chatassist.common.dto.StatusUpdateRequest request) {
                throw new AssertionError("updateStatus should not be called for invalid payloads");
            }
        };

        mockMvc = buildMockMvc(chatMessagingService);
    }

    private MockMvc buildMockMvc(ChatMessagingService chatMessagingService) {
        return MockMvcBuilders.standaloneSetup(new ChatController(chatMessagingService))
                .setControllerAdvice(new ChatControllerAdvice())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("sendMessage rejects invalid payloads with field errors")
    void sendMessageRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/chats/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "senderId": 0,
                                  "senderUsername": "ab",
                                  "receiverId": null,
                                  "receiverUsername": null,
                                  "content": "   ",
                                  "messageType": null,
                                  "contextUsername": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed."))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.fieldErrors.senderId").value("Sender id must be a positive number."))
                .andExpect(jsonPath("$.fieldErrors.senderUsername").value("Sender username must be between 3 and 50 characters."))
                .andExpect(jsonPath("$.fieldErrors.receiverId").value("Receiver id is required."))
                .andExpect(jsonPath("$.fieldErrors.receiverUsername").value("Receiver username is required."))
                .andExpect(jsonPath("$.fieldErrors.content").value("Message content is required."))
                .andExpect(jsonPath("$.fieldErrors.messageType").value("Message type is required."))
                .andExpect(jsonPath("$.fieldErrors.contextUsername").value("Context username must be 50 characters or fewer."));
    }

    @Test
    @DisplayName("updateStatus rejects invalid payloads with field errors")
    void updateStatusRejectsInvalidPayload() throws Exception {
        mockMvc.perform(patch("/api/chats/messages/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messageId": null,
                                  "status": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed."))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.fieldErrors.messageId").value("Message id is required."))
                .andExpect(jsonPath("$.fieldErrors.status").value("Message status is required."));
    }

    @Test
    @DisplayName("updateStatus maps response status errors to API error response")
    void updateStatusMapsResponseStatusException() throws Exception {
        ChatMessagingService notFoundService = new ChatMessagingService(
                null,
                new ChatMessageMapper(),
                new ChatEventPublisher(null),
                new WebSocketNotifier(null),
                RestClient.create()
        ) {
            @Override
            public com.chatassist.common.dto.ChatMessageResponse updateStatus(com.chatassist.common.dto.StatusUpdateRequest request) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found");
            }
        };
        mockMvc = buildMockMvc(notFoundService);

        mockMvc.perform(patch("/api/chats/messages/status")
                        .header("X-Username", "alex")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messageId": 100,
                                  "status": "SEEN"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Message not found"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }
}

