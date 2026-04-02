package com.chatassist.bot.service;

import com.chatassist.common.dto.UserSummary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class BotDirectoryClient {

    private final RestClient restClient;
    private final String userServiceBaseUrl;

    public BotDirectoryClient(RestClient restClient,
                              @LoadBalanced RestClient.Builder loadBalancedRestClientBuilder,
                              @Value("${chatassist.discovery.enabled:true}") boolean discoveryEnabled,
                              @Value("${services.user-service.base-url}") String userServiceBaseUrl) {
        this.restClient = discoveryEnabled ? loadBalancedRestClientBuilder.build() : restClient;
        this.userServiceBaseUrl = discoveryEnabled ? "http://user-service" : userServiceBaseUrl;
    }

    public UserSummary getByUsername(String username) {
        return restClient.get()
                .uri(userServiceBaseUrl + "/api/users/by-username/{username}", username)
                .retrieve()
                .body(UserSummary.class);
    }
}
