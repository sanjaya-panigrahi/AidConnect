package com.chatassist.chat.service;

import com.chatassist.common.dto.ChatMessageResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class WebSocketNotifier {

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketNotifier(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void notifyMessage(ChatMessageResponse response) {
        messagingTemplate.convertAndSend("/topic/messages/" + response.receiverUsername(), response);
        messagingTemplate.convertAndSend("/topic/messages/" + response.senderUsername(), response);
        messagingTemplate.convertAndSend("/topic/status/" + response.receiverUsername(), response);
        messagingTemplate.convertAndSend("/topic/status/" + response.senderUsername(), response);
        // When an assistant replies in a user-user conversation context, also notify the
        // other participant so they see the response in their shared thread in real time.
        if (response.contextUsername() != null) {
            messagingTemplate.convertAndSend("/topic/messages/" + response.contextUsername(), response);
            messagingTemplate.convertAndSend("/topic/status/" + response.contextUsername(), response);
        }
    }
}
