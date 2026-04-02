package com.chatassist.bot.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Redis configuration for bot-service.
 *
 * Cache keys and TTLs
 * ─────────────────────────────────────
 * bot-svc:history:{username}  – 60 min sliding  (conversation window per user)
 * ─────────────────────────────────────
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    /** Key prefix for conversation history entries in Redis. */
    public static final String HISTORY_KEY_PREFIX = "bot-svc:history:";

    /** How long a user's history survives without activity. */
    public static final Duration HISTORY_TTL = Duration.ofMinutes(60);

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory cf) {
        return new StringRedisTemplate(cf);
    }
}

