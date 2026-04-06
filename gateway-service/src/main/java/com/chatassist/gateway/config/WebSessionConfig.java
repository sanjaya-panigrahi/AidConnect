package com.chatassist.gateway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.server.EnableRedisWebSession;

@Configuration
@EnableRedisWebSession(maxInactiveIntervalInSeconds = 300, redisNamespace = "chatassist:session")
public class WebSessionConfig {
}

