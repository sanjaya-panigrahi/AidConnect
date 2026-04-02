package com.chatassist.bot.service;

import com.chatassist.common.dto.ChatMessageRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class BotReplyClient {

    private final RestClient restClient;
    private final String chatServiceBaseUrl;

    public BotReplyClient(RestClient restClient,
                          @LoadBalanced RestClient.Builder loadBalancedRestClientBuilder,
                          @Value("${chatassist.discovery.enabled:true}") boolean discoveryEnabled,
                          @Value("${services.chat-service.base-url}") String chatServiceBaseUrl) {
        this.restClient = discoveryEnabled ? loadBalancedRestClientBuilder.build() : restClient;
        this.chatServiceBaseUrl = discoveryEnabled ? "http://chat-service" : chatServiceBaseUrl;
    }

    public void send(ChatMessageRequest request) {
        restClient.post()
                .uri(chatServiceBaseUrl + "/api/chats/messages/internal")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }
}
