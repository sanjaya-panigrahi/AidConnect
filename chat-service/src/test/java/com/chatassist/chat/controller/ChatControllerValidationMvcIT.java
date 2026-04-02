package com.chatassist.chat.controller;

import com.chatassist.chat.service.ChatEventPublisher;
import com.chatassist.chat.service.ChatMessageMapper;
import com.chatassist.chat.service.ChatMessagingService;
import com.chatassist.chat.service.WebSocketNotifier;
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
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = ChatControllerValidationMvcIT.TestApplication.class,
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
class ChatControllerValidationMvcIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("sendMessage returns field-level validation contract from full MVC context")
    void sendMessageValidationContract() throws Exception {
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
    @DisplayName("updateStatus maps ResponseStatusException to API error contract from full MVC context")
    void updateStatusNotFoundContract() throws Exception {
        mockMvc.perform(patch("/api/chats/messages/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messageId": 404,
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

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({ChatController.class, ChatControllerAdvice.class})
    static class TestApplication {

        @Bean
        ChatMessagingService chatMessagingService() {
            return new ChatMessagingService(
                    null,
                    new ChatMessageMapper(),
                    new ChatEventPublisher(null),
                    new WebSocketNotifier(null),
                    RestClient.create()
            ) {
                @Override
                public com.chatassist.common.dto.ChatMessageResponse send(com.chatassist.common.dto.ChatMessageRequest request,
                                                                          boolean generatedByBot) {
                    throw new AssertionError("send should not be called for invalid payloads");
                }

                @Override
                public com.chatassist.common.dto.ChatMessageResponse updateStatus(com.chatassist.common.dto.StatusUpdateRequest request) {
                    if (Long.valueOf(404L).equals(request.messageId())) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found");
                    }
                    throw new AssertionError("updateStatus is not expected for this payload");
                }
            };
        }
    }
}

