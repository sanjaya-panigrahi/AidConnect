package com.chatassist.bot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger UI Configuration for Bot Service.
 * Exposes API documentation via:
 * - Swagger UI: http://localhost:8083/swagger-ui/index.html
 * - OpenAPI JSON: http://localhost:8083/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI botServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AidConnect - Bot Service API")
                        .description("Kafka-based bot assistance service for AI-powered chat responses.")
                        .version("v1")
                        .contact(new Contact().name("AidConnect Team")))
                .servers(List.of(new Server().url("/").description("Current environment")));
    }
}

