package com.chatassist.bot.service;

import com.chatassist.common.dto.ChatMessageEvent;
import com.chatassist.common.dto.ChatMessageRequest;
import com.chatassist.common.dto.ChatMessageResponse;
import com.chatassist.common.model.MessageStatus;
import com.chatassist.common.model.MessageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BotMessageConsumerTest {

    @Mock
    private BotReplyClient replyClient;

    @Mock
    private AiAssistantService aiAssistantService;

    @Mock
    private ChatHistoryClient chatHistoryClient;

    @InjectMocks
    private BotMessageConsumer consumer;

    @Test
    void consume_fetchesHistoryAndSendsReply_forDirectBotChat() {
        ChatMessageEvent event = new ChatMessageEvent(
                1L,
                100L,
                "alice",
                2L,
                "bot",
                null,
                "What is hallucination?",
                MessageType.BOT,
                MessageStatus.DELIVERED,
                Instant.now(),
                Instant.now(),
                null,
                false
        );

        List<ChatMessageResponse> history = List.of(
                new ChatMessageResponse(11L, 100L, "alice", 2L, "bot", "Hello", MessageType.BOT, MessageStatus.SEEN, Instant.now(), Instant.now(), Instant.now(), null),
                new ChatMessageResponse(12L, 2L, "bot", 100L, "alice", "Hi there", MessageType.BOT, MessageStatus.SEEN, Instant.now(), Instant.now(), Instant.now(), null)
        );

        when(chatHistoryClient.getConversationHistory("alice", "bot")).thenReturn(history);
        when(aiAssistantService.reply("What is hallucination?", history)).thenReturn("A hallucination is... ");

        consumer.consume(event);

        verify(chatHistoryClient).getConversationHistory("alice", "bot");
        verify(aiAssistantService).reply("What is hallucination?", history);

        ArgumentCaptor<ChatMessageRequest> requestCaptor = ArgumentCaptor.forClass(ChatMessageRequest.class);
        verify(replyClient).send(requestCaptor.capture());
        ChatMessageRequest sent = requestCaptor.getValue();

        assertThat(sent.senderUsername()).isEqualTo("bot");
        assertThat(sent.receiverUsername()).isEqualTo("alice");
        assertThat(sent.content()).isEqualTo("A hallucination is... ");
        assertThat(sent.messageType()).isEqualTo(MessageType.BOT);
        assertThat(sent.contextUsername()).isNull();

        // Cache must be updated so follow-ups skip the HTTP fetch.
        verify(chatHistoryClient).appendExchange(eq("alice"), any(ChatMessageResponse.class), any(ChatMessageResponse.class));
    }   // ← close consume_fetchesHistoryAndSendsReply_forDirectBotChat

    @Test
    void consume_ignoresMessagesGeneratedByBot() {
        ChatMessageEvent event = new ChatMessageEvent(
                2L,
                2L,
                "bot",
                100L,
                "alice",
                null,
                "Auto reply",
                MessageType.BOT,
                MessageStatus.DELIVERED,
                Instant.now(),
                Instant.now(),
                null,
                true
        );

        consumer.consume(event);

        verify(chatHistoryClient, never()).getConversationHistory("bot", "bot");
        verify(aiAssistantService, never()).reply(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList());
        verify(replyClient, never()).send(org.mockito.ArgumentMatchers.any(ChatMessageRequest.class));
    }

    @Test
    void consume_handlesMentionInRegularChat() {
        ChatMessageEvent event = new ChatMessageEvent(
                3L,
                101L,
                "bob",
                102L,
                "carol",
                null,
                "@bot explain this error",
                MessageType.USER,
                MessageStatus.DELIVERED,
                Instant.now(),
                Instant.now(),
                null,
                false
        );

        when(chatHistoryClient.getConversationHistory("bob", "bot")).thenReturn(List.of());
        when(aiAssistantService.reply("explain this error", List.of())).thenReturn("Here is the explanation");

        consumer.consume(event);

        verify(chatHistoryClient).getConversationHistory("bob", "bot");
        verify(aiAssistantService).reply("explain this error", List.of());
        verify(replyClient).send(org.mockito.ArgumentMatchers.any(ChatMessageRequest.class));

        // Cache must be updated after mention-routed replies too.
        verify(chatHistoryClient).appendExchange(eq("bob"), any(ChatMessageResponse.class), any(ChatMessageResponse.class));
    }
}

