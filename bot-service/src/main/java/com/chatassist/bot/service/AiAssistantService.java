package com.chatassist.bot.service;

import com.chatassist.common.dto.ChatMessageResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiAssistantService {

    private static final int MAX_HISTORY_MESSAGES = 20;
    private static final int MAX_HISTORY_CHARS = 5000;

    private final ChatClient chatClient;

    public AiAssistantService(ObjectProvider<ChatClient.Builder> builderProvider) {
        ChatClient.Builder builder = builderProvider.getIfAvailable();
        this.chatClient = builder != null ? builder.build() : null;
    }

    public String reply(String prompt, List<ChatMessageResponse> history) {
        if (chatClient == null) {
            return "AI assistant is running in fallback mode. I received: " + prompt;
        }

        String historyContext = buildHistoryContext(history);
        String userPrompt = historyContext.isBlank()
                ? prompt
                : "Conversation so far:\n" + historyContext + "\n\nLatest user message:\n" + prompt;

        return chatClient.prompt()
                .system("You are a concise one-to-one chat assistant inside a customer messaging app. Keep answers short, helpful, and conversational.")
                .user(userPrompt)
                .call()
                .content();
    }

    private String buildHistoryContext(List<ChatMessageResponse> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }

        int start = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < history.size(); i++) {
            ChatMessageResponse message = history.get(i);
            String content = message.content();
            if (content == null || content.isBlank()) {
                continue;
            }

            String role = "bot".equalsIgnoreCase(message.senderUsername()) ? "Assistant" : "User";
            String line = role + ": " + content.strip() + "\n";
            if (builder.length() + line.length() > MAX_HISTORY_CHARS) {
                break;
            }
            builder.append(line);
        }
        return builder.toString().strip();
    }
}
