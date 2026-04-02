package com.chatassist.chat.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration.
 * CORS is handled centrally by the API Gateway (gateway-service).
 * No per-service CORS mapping needed.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
}
