package com.chatassist.chat.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI chatServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Chat Assist - Chat Service API")
                        .description("Messaging APIs for direct chat, assistants, and message status tracking.")
                        .version("v1")
                        .contact(new Contact().name("Chat Assist Team")))
                .servers(List.of(new Server().url("/").description("Current environment")));
    }
}

