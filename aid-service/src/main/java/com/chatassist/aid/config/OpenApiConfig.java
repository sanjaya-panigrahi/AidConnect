package com.chatassist.aid.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger UI Configuration for AID Service.
 * Exposes API documentation via:
 * - Swagger UI: http://localhost:8084/swagger-ui/index.html
 * - OpenAPI JSON: http://localhost:8084/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI aidServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AidConnect - AID Service API")
                        .description("Appointment scheduling and assistance service for handling appointment booking inquiries.")
                        .version("v1")
                        .contact(new Contact().name("AidConnect Team")))
                .servers(List.of(new Server().url("/").description("Current environment")));
    }
}

