package com.chatassist.bot.service;

import com.chatassist.common.dto.ChatMessageResponse;
import com.chatassist.common.model.MessageStatus;
import com.chatassist.common.model.MessageType;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class AiAssistantServiceTest {

    @Test
    void fallbackWhenNoChatClientBuilder() {
        ObjectProvider<ChatClient.Builder> emptyProvider =
                new StaticListableBeanFactory().getBeanProvider(ChatClient.Builder.class);

        AiAssistantService service = new AiAssistantService(emptyProvider);

        String reply = service.reply("Hello", List.of());
        assertThat(reply).contains("fallback mode").contains("Hello");
    }

    @Test
    void replyUsesHistoryAndReturnsModelContent() {
        ChatClient fakeClient = buildFakeChatClient("Short answer from model.", new AtomicBoolean(false));
        AiAssistantService service = new AiAssistantService(providerFor(fakeClient));

        List<ChatMessageResponse> history = List.of(
                new ChatMessageResponse(1L, 100L, "alice", 2L, "bot", "Hi",
                        MessageType.USER, MessageStatus.DELIVERED, Instant.now(), Instant.now(), null, null),
                new ChatMessageResponse(2L, 2L, "bot", 100L, "alice", "Hello!",
                        MessageType.BOT, MessageStatus.DELIVERED, Instant.now(), Instant.now(), null, null)
        );

        String reply = service.reply("How are you?", history);
        assertThat(reply).isEqualTo("Short answer from model.");
    }

    @Test
    void registersToolForPromptChain() {
        AtomicBoolean toolsCalled = new AtomicBoolean(false);
        ChatClient fakeClient = buildFakeChatClient("Current UTC time is available.", toolsCalled);
        AiAssistantService service = new AiAssistantService(providerFor(fakeClient));

        String reply = service.reply("what time is it", List.of());

        assertThat(reply).isEqualTo("Current UTC time is available.");
        assertThat(toolsCalled).isTrue();
    }

    private ObjectProvider<ChatClient.Builder> providerFor(ChatClient client) {
        ChatClient.Builder builder = proxy(ChatClient.Builder.class, (p, m, a) ->
                "build".equals(m.getName()) ? client : p);
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        factory.addBean("chatClientBuilder", builder);
        return factory.getBeanProvider(ChatClient.Builder.class);
    }

    private ChatClient buildFakeChatClient(String content, AtomicBoolean toolsCalled) {
        return proxy(ChatClient.class, (proxy, method, args) -> {
            if ("prompt".equals(method.getName())) {
                return buildPromptChainProxy(content, toolsCalled);
            }
            return defaultValue(method.getReturnType());
        });
    }

    private Object buildPromptChainProxy(String content, AtomicBoolean toolsCalled) {
        return Proxy.newProxyInstance(
                ChatClient.ChatClientRequestSpec.class.getClassLoader(),
                new Class[]{ChatClient.ChatClientRequestSpec.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "system", "user" -> proxy;
                    case "tools" -> {
                        toolsCalled.set(true);
                        invokeTimeToolIfPresent(args);
                        yield proxy;
                    }
                    case "call" -> buildCallProxy(content);
                    default -> defaultValue(method.getReturnType());
                });
    }

    private void invokeTimeToolIfPresent(Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) {
            return;
        }

        Object toolCandidate = args[0];
        if (toolCandidate instanceof Object[] arr && arr.length > 0) {
            toolCandidate = arr[0];
        }
        if (toolCandidate == null) {
            return;
        }

        try {
            var method = toolCandidate.getClass().getDeclaredMethod("getCurrentUtcTime");
            method.setAccessible(true);
            Object value = method.invoke(toolCandidate);
            assertThat(value).isInstanceOf(String.class);
            assertThat(((String) value)).isNotBlank();
        } catch (ReflectiveOperationException ignored) {
            // Ignore if this isn't the expected tool object.
        }
    }

    private Object buildCallProxy(String content) {
        return Proxy.newProxyInstance(
                ChatClient.CallResponseSpec.class.getClassLoader(),
                new Class[]{ChatClient.CallResponseSpec.class},
                (proxy, method, args) -> "content".equals(method.getName())
                        ? content
                        : defaultValue(method.getReturnType()));
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> iface, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class[]{iface}, handler);
    }

    private Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
}

