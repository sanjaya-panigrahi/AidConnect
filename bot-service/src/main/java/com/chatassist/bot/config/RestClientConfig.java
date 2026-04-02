package com.chatassist.bot.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    /**
     * Plain (non-load-balanced) RestClient for calling external services such as api.openai.com.
     * Marked @Primary so Spring AI's OpenAiAutoConfiguration picks this builder instead of the
     * load-balanced one, preventing the Cloud LoadBalancer from intercepting OpenAI API calls.
     */
    @Bean
    @Primary
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    /**
     * Plain RestClient instance used by internal clients when service discovery is disabled.
     */
    @Bean
    RestClient restClient() {
        return RestClient.builder().build();
    }

    /**
     * Load-balanced RestClient.Builder for intra-cluster calls resolved via Eureka
     * (e.g. http://chat-service, http://user-service).
     * BotReplyClient and BotDirectoryClient inject this explicitly via @LoadBalanced.
     */
    @Bean
    @LoadBalanced
    RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }
}
