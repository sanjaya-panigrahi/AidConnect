package com.chatassist.chat.service;

import com.chatassist.common.dto.ChatMessageEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class ChatEventPublisher {

    private final KafkaTemplate<String, ChatMessageEvent> kafkaTemplate;

    public ChatEventPublisher(KafkaTemplate<String, ChatMessageEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(ChatMessageEvent event) {
        kafkaTemplate.send("chat-messages", event.receiverUsername(), event);
    }
}
