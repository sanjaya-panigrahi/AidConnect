package com.chatassist.user.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Redis-backed cache configuration for user-service.
 *
 * Cache names and TTLs
 * ─────────────────────────────────────────────
 * users:list          – 5 min (user list per excludeUsername)
 * users:profile       – 5 min (single user by username)
 * users:assistants    – 5 min (static assistant registry)
 * users:activity      – 5 min (per-user today activity counts)
 * users:activity:all  – 5 min (all-users today activity counts)
 * ─────────────────────────────────────────────
 * All keys are prefixed with "user-svc::" to avoid collisions
 * when services share the same Redis instance.
 */
@Configuration
@EnableCaching
public class RedisCacheConfig implements CachingConfigurer {
    private static final Logger log = LoggerFactory.getLogger(RedisCacheConfig.class);
    private final RedisConnectionFactory redisConnectionFactory;

    // ── Cache name constants (also used in service annotations) ──────────────
    public static final String USERS_LIST          = "users:list";
    public static final String USER_PROFILE        = "users:profile";
    public static final String ASSISTANTS          = "users:assistants";
    public static final String USER_ACTIVITY       = "users:activity";
    public static final String ALL_USER_ACTIVITY   = "users:activity:all";

    public RedisCacheConfig(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Bean
    @Override
    public CacheManager cacheManager() {
        RedisCacheConfiguration base = defaultConfig();

        Map<String, RedisCacheConfiguration> perCache = Map.of(
                USERS_LIST,        base.entryTtl(Duration.ofMinutes(5)),
                USER_PROFILE,      base.entryTtl(Duration.ofMinutes(5)),
                ASSISTANTS,        base.entryTtl(Duration.ofMinutes(5)),
                USER_ACTIVITY,     base.entryTtl(Duration.ofMinutes(5)),
                ALL_USER_ACTIVITY, base.entryTtl(Duration.ofMinutes(5))
        );

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(base.entryTtl(Duration.ofMinutes(5)))
                .withInitialCacheConfigurations(perCache)
                .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache GET error (will skip cache and call source): cache={}, key={}", cache.getName(), key, e);
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Cache PUT error (will skip cache write): cache={}, key={}", cache.getName(), key, e);
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache EVICT error: cache={}, key={}", cache.getName(), key, e);
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Cache CLEAR error: cache={}", cache.getName(), e);
            }
        };
    }

    private RedisCacheConfiguration defaultConfig() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Use EVERYTHING to include type info for all types including Lists
        objectMapper.activateDefaultTypingAsProperty(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.EVERYTHING,
                "@class");

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        return RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith("user-svc:v3::")
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .disableCachingNullValues();
    }
}