package com.chatassist.user.config;

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
    public OpenAPI userServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Chat Assist - User Service API")
                        .description("Authentication, user directory, assistant listing, presence, and activity APIs.")
                        .version("v1")
                        .contact(new Contact().name("Chat Assist Team")))
                .servers(List.of(new Server().url("/").description("Current environment")));
    }
}

