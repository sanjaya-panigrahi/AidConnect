package com.chatassist.chat.controller;

import com.chatassist.chat.service.ChatEventPublisher;
import com.chatassist.chat.service.ChatMessageMapper;
import com.chatassist.chat.service.ChatMessagingService;
import com.chatassist.chat.service.WebSocketNotifier;
import com.chatassist.common.dto.ChatMessageRequest;
import com.chatassist.common.dto.ChatMessageResponse;
import com.chatassist.common.dto.DailyChatPeerSummary;
import com.chatassist.common.dto.StatusUpdateRequest;
import com.chatassist.common.model.MessageStatus;
import com.chatassist.common.model.MessageType;
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
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(RestDocumentationExtension.class)
class ChatControllerRestDocsTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        ChatMessagingService service = new ChatMessagingService(
                null,
                new ChatMessageMapper(),
                new ChatEventPublisher(null),
                new WebSocketNotifier(null),
                RestClient.create()
        ) {
            @Override
            public ChatMessageResponse send(ChatMessageRequest request, boolean generatedByBot) {
                Instant now = Instant.parse("2026-03-31T16:00:00Z");
                return new ChatMessageResponse(
                        301L,
                        request.senderId(),
                        request.senderUsername(),
                        request.receiverId(),
                        request.receiverUsername(),
                        request.content(),
                        request.messageType(),
                        MessageStatus.DELIVERED,
                        now,
                        now,
                        null,
                        request.contextUsername()
                );
            }

            @Override
            public List<ChatMessageResponse> getConversation(String userA, String userB) {
                Instant now = Instant.parse("2026-03-31T16:05:00Z");
                return List.of(new ChatMessageResponse(
                        302L,
                        10L,
                        userA,
                        11L,
                        userB,
                        "Hi from conversation history",
                        MessageType.USER,
                        MessageStatus.SEEN,
                        now,
                        now,
                        now,
                        null
                ));
            }

            @Override
            public DailyChatPeerSummary getDailyChatPeerSummary(String username) {
                return new DailyChatPeerSummary(username, LocalDate.of(2026, 3, 31), 3);
            }

            @Override
            public List<DailyChatPeerSummary> getAllDailyChatPeerSummaries() {
                return List.of(new DailyChatPeerSummary("alex", LocalDate.of(2026, 3, 31), 5));
            }

            @Override
            public ChatMessageResponse updateStatus(StatusUpdateRequest request) {
                Instant now = Instant.parse("2026-03-31T16:10:00Z");
                return new ChatMessageResponse(
                        request.messageId(),
                        10L,
                        "alex",
                        11L,
                        "bot",
                        "Status updated",
                        MessageType.BOT,
                        request.status(),
                        now,
                        now,
                        request.status() == MessageStatus.SEEN ? now : null,
                        null
                );
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(new ChatController(service))
                .setControllerAdvice(new ChatControllerAdvice())
                .setValidator(validator)
                .apply(documentationConfiguration(restDocumentation))
                .build();
    }

    @Test
    void documentSendMessage() throws Exception {
        mockMvc.perform(post("/api/chats/messages")
                        .header("X-User-Id", "10")
                        .header("X-Username", "alex")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "senderId": 10,
                                  "senderUsername": "alex",
                                  "receiverId": 11,
                                  "receiverUsername": "bot",
                                  "content": "Hello @bot",
                                  "messageType": "BOT",
                                  "contextUsername": null
                                }
                                """))
                .andExpect(status().isOk())
                .andDo(document("chat-send-message",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("senderId").type(JsonFieldType.NUMBER).description("Numeric sender ID."),
                                fieldWithPath("senderUsername").type(JsonFieldType.STRING).description("Sender username."),
                                fieldWithPath("receiverId").type(JsonFieldType.NUMBER).description("Receiver ID."),
                                fieldWithPath("receiverUsername").type(JsonFieldType.STRING).description("Receiver username."),
                                fieldWithPath("content").type(JsonFieldType.STRING).description("Message body."),
                                fieldWithPath("messageType").type(JsonFieldType.STRING).description("Message type enum: USER/BOT."),
                                fieldWithPath("contextUsername").type(JsonFieldType.NULL).optional().description("Optional context for assistant replies in routed conversations.")
                        ),
                        responseFields(
                                fieldWithPath("id").type(JsonFieldType.NUMBER).description("Persisted message ID."),
                                fieldWithPath("senderId").type(JsonFieldType.NUMBER).description("Sender ID."),
                                fieldWithPath("senderUsername").type(JsonFieldType.STRING).description("Sender username."),
                                fieldWithPath("receiverId").type(JsonFieldType.NUMBER).description("Receiver ID."),
                                fieldWithPath("receiverUsername").type(JsonFieldType.STRING).description("Receiver username."),
                                fieldWithPath("content").type(JsonFieldType.STRING).description("Message content."),
                                fieldWithPath("messageType").type(JsonFieldType.STRING).description("Message type enum."),
                                fieldWithPath("status").type(JsonFieldType.STRING).description("Delivery state enum."),
                                fieldWithPath("sentAt").type(JsonFieldType.NUMBER).description("Message creation timestamp."),
                                fieldWithPath("deliveredAt").type(JsonFieldType.NUMBER).description("Delivery timestamp."),
                                fieldWithPath("seenAt").type(JsonFieldType.NULL).optional().description("Seen timestamp when available."),
                                fieldWithPath("contextUsername").type(JsonFieldType.NULL).optional().description("Context username when assistant fan-out applies.")
                        )));
    }

    @Test
    void documentGetConversation() throws Exception {
        mockMvc.perform(get("/api/chats/conversation")
                        .header("X-Username", "alex")
                        .param("userA", "alex")
                        .param("userB", "bot"))
                .andExpect(status().isOk())
                .andDo(document("chat-get-conversation",
                        preprocessResponse(prettyPrint()),
                        queryParameters(
                                parameterWithName("userA").description("First username in the conversation."),
                                parameterWithName("userB").description("Second username in the conversation.")
                        ),
                        responseFields(
                                fieldWithPath("[].id").description("Message ID."),
                                fieldWithPath("[].senderId").description("Sender ID."),
                                fieldWithPath("[].senderUsername").description("Sender username."),
                                fieldWithPath("[].receiverId").description("Receiver ID."),
                                fieldWithPath("[].receiverUsername").description("Receiver username."),
                                fieldWithPath("[].content").description("Message content."),
                                fieldWithPath("[].messageType").description("Message type enum."),
                                fieldWithPath("[].status").description("Message status enum."),
                                fieldWithPath("[].sentAt").type(JsonFieldType.NUMBER).description("Sent timestamp."),
                                fieldWithPath("[].deliveredAt").type(JsonFieldType.NUMBER).description("Delivered timestamp."),
                                fieldWithPath("[].seenAt").type(JsonFieldType.NUMBER).description("Seen timestamp, when available."),
                                fieldWithPath("[].contextUsername").optional().description("Context username for assistant-in-thread replies.")
                        )));
    }

    @Test
    void documentUpdateStatus() throws Exception {
        mockMvc.perform(patch("/api/chats/messages/status")
                        .header("X-Username", "alex")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messageId": 302,
                                  "status": "SEEN"
                                }
                                """))
                .andExpect(status().isOk())
                .andDo(document("chat-update-status",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("messageId").type(JsonFieldType.NUMBER).description("Message ID to update."),
                                fieldWithPath("status").type(JsonFieldType.STRING).description("New message status enum.")
                        ),
                        responseFields(
                                fieldWithPath("id").type(JsonFieldType.NUMBER).description("Message ID."),
                                fieldWithPath("senderId").type(JsonFieldType.NUMBER).description("Sender ID."),
                                fieldWithPath("senderUsername").type(JsonFieldType.STRING).description("Sender username."),
                                fieldWithPath("receiverId").type(JsonFieldType.NUMBER).description("Receiver ID."),
                                fieldWithPath("receiverUsername").type(JsonFieldType.STRING).description("Receiver username."),
                                fieldWithPath("content").type(JsonFieldType.STRING).description("Message content."),
                                fieldWithPath("messageType").type(JsonFieldType.STRING).description("Message type enum."),
                                fieldWithPath("status").type(JsonFieldType.STRING).description("Updated status enum."),
                                fieldWithPath("sentAt").type(JsonFieldType.NUMBER).description("Sent timestamp."),
                                fieldWithPath("deliveredAt").type(JsonFieldType.NUMBER).description("Delivered timestamp."),
                                fieldWithPath("seenAt").type(JsonFieldType.NUMBER).optional().description("Seen timestamp when status is SEEN."),
                                fieldWithPath("contextUsername").type(JsonFieldType.NULL).optional().description("Context username when present.")
                        )));
    }

    @Test
    void documentValidationErrorContract() throws Exception {
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
                .andDo(document("chat-validation-error",
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

