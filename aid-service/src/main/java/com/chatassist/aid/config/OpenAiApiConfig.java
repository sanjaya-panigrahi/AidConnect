package com.chatassist.aid.config;

import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class OpenAiApiConfig {

    @Bean
    public OpenAiApi openAiApi(@Value("${spring.ai.openai.api-key:}") String apiKey) {
        return OpenAiApi.builder()
                .apiKey(apiKey)
                // Use a plain builder instance so external calls are not intercepted by Eureka LB.
                .restClientBuilder(RestClient.builder())
                .build();
    }
}

