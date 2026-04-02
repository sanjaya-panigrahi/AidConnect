package com.chatassist.chat.service;

import com.chatassist.chat.entity.ChatMessage;
import com.chatassist.chat.repository.ChatMessageRepository;
import com.chatassist.common.dto.ChatMessageEvent;
import com.chatassist.common.dto.ChatMessageResponse;
import com.chatassist.common.dto.ChatMessageRequest;
import com.chatassist.common.model.MessageType;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessagingServiceGuestGuardrailTest {

    @Test
    void sendSkipsPresenceUpdateForGuestUsernames() {
        ChatMessageRepository repository = proxyRepository();
        RecordingPublisher eventPublisher = new RecordingPublisher();
        RecordingNotifier webSocketNotifier = new RecordingNotifier();
        TestableChatMessagingService service = new TestableChatMessagingService(
                repository,
                new ChatMessageMapper(),
                eventPublisher,
                webSocketNotifier
        );

        service.send(buildRequest("guest-ab12cd"), false);

        assertThat(service.markOnlineCallCount).isZero();
        assertThat(webSocketNotifier.callCount).isEqualTo(1);
        assertThat(eventPublisher.callCount).isEqualTo(1);
    }

    @Test
    void sendStillMarksOnlineForRegularUsers() {
        ChatMessageRepository repository = proxyRepository();
        RecordingPublisher eventPublisher = new RecordingPublisher();
        RecordingNotifier webSocketNotifier = new RecordingNotifier();
        TestableChatMessagingService service = new TestableChatMessagingService(
                repository,
                new ChatMessageMapper(),
                eventPublisher,
                webSocketNotifier
        );

        service.send(buildRequest("sanjaya"), false);

        assertThat(service.markOnlineCallCount).isEqualTo(1);
        assertThat(service.lastMarkedUsername).isEqualTo("sanjaya");
        assertThat(webSocketNotifier.callCount).isEqualTo(1);
        assertThat(eventPublisher.callCount).isEqualTo(1);
    }

    private ChatMessageRequest buildRequest(String senderUsername) {
        return new ChatMessageRequest(
                101L,
                senderUsername,
                -1L,
                "bot",
                "hello @bot",
                MessageType.BOT,
                null
        );
    }

    private ChatMessageRepository proxyRepository() {
        return (ChatMessageRepository) Proxy.newProxyInstance(
                ChatMessageRepository.class.getClassLoader(),
                new Class[]{ChatMessageRepository.class},
                (proxy, method, args) -> {
                    if ("save".equals(method.getName())) {
                        return args[0];
                    }
                    throw new UnsupportedOperationException("Unexpected repository call in test: " + method.getName());
                }
        );
    }

    private static class TestableChatMessagingService extends ChatMessagingService {
        private int markOnlineCallCount;
        private String lastMarkedUsername;

        TestableChatMessagingService(ChatMessageRepository repository,
                                     ChatMessageMapper mapper,
                                     ChatEventPublisher eventPublisher,
                                     WebSocketNotifier webSocketNotifier) {
            super(repository, mapper, eventPublisher, webSocketNotifier, null);
        }

        @Override
        protected void markSenderOnlineAsync(String username) {
            markOnlineCallCount++;
            lastMarkedUsername = username;
        }
    }

    private static class RecordingPublisher extends ChatEventPublisher {
        private int callCount;

        RecordingPublisher() {
            super((KafkaTemplate<String, ChatMessageEvent>) null);
        }

        @Override
        public void publish(ChatMessageEvent event) {
            callCount++;
        }
    }

    private static class RecordingNotifier extends WebSocketNotifier {
        private int callCount;

        RecordingNotifier() {
            super((SimpMessagingTemplate) null);
        }

        @Override
        public void notifyMessage(ChatMessageResponse response) {
            callCount++;
        }
    }
}

