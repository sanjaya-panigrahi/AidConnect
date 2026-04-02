package com.chatassist.user.config;

import com.chatassist.user.service.UserService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserBootstrapConfig {

    @Bean
    CommandLineRunner onStartup(UserService userService) {
        return args -> userService.removeLegacyBotUsers();
    }
}
